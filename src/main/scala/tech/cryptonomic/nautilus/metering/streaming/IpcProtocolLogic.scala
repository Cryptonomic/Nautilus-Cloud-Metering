package tech.cryptonomic.nautilus.metering.streaming

import java.util.UUID

import akka.stream.stage.{GraphStageLogic, InHandler, OutHandler}
import akka.stream.{FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.generic.auto._
import io.circe.parser._

import scala.util.matching.Regex

/**
  * Handles all the logic element of the protocol including checking if the client
  * is speaking an understood protocol and parsing of valid request objects from
  * the raw byte stream.
  *
  * @param shape A ByteStream => HttpRequest flow shape.
  */
class IpcProtocolLogic(shape: FlowShape[ByteString, HttpRequest])
    extends GraphStageLogic(shape)
    with InHandler
    with OutHandler
    with ErrorAccumulatingCirceSupport
    with LazyLogging {

  object State extends Enumeration {
    type State = Value
    val INIT, PREAMBLE_COMPLETE, HEADER_COMPLETE, DATA_COMPLETE = Value
  }

  val version = "01"
  private val connId = UUID.randomUUID()
  private val preamble = "meter.F3."
  private val delimiter = "\r\n"
  private val payloadSizePattern: Regex = """^Length: (\d+)\r\n""".r
  private val buffer = new StringBuffer(1024)
  private var state = State.INIT
  private var payloadSize = 0
  private var upstreamFinished = false
  private val in: Inlet[ByteString] = shape.in
  private val out: Outlet[HttpRequest] = shape.out

  setHandler(out, this)
  setHandler(in, this)

  private def continue(): Unit =
    if (isClosed(in) | upstreamFinished) {
      throw UpstreamUnavailableException()
    } else pull(in)

  private def emit(response: HttpRequest): Unit =
    if (isAvailable(out)) emit(out, response)
    else throw UnknownProtocolException()

  private def processPreamble(): Unit =
    if (buffer.length() < (preamble.length + version.length + delimiter.length)) {
      continue() // Need more data
    } else if (buffer.indexOf(preamble) != 0) {
      throw InvalidPreambleException()
    } else if (buffer.indexOf(version, preamble.length) != preamble.length) {
      throw InvalidVersionException()
    } else if (buffer.indexOf(delimiter, preamble.length + version.length) != preamble.length + version.length) {
      throw InvalidDelimiterException()
    } else {
      buffer.delete(0, preamble.length + version.length + delimiter.length)
      state = State.PREAMBLE_COMPLETE
      consume()
    }

  private def processContentLength(): Unit =
    payloadSizePattern.findFirstMatchIn(buffer).map { m =>
      val size = m.group(1).toInt // Convert before deleting, match has shallow copy
      buffer.delete(0, m.group(0).length)
      size
    } match {
      case Some(x) if x < 0 => // The size obviously cannot be negative
        throw InvalidPayloadLengthException()
      case Some(x) =>
        state = State.HEADER_COMPLETE
        payloadSize = x
        consume()
      case None =>
        logger.debug(s"Connection: $connId, State: ${state.toString}, Could not find a valid content length line")
        continue()
    }

  private def processContent(): Unit =
    if (buffer.length() >= payloadSize) {
      state = State.DATA_COMPLETE
      decode[HttpRequest](buffer.substring(0, payloadSize)) match {
        case Left(ex) =>
          ex.printStackTrace()
          throw PayloadDecodingException()
        case Right(request) =>
          emit(request)
      }
    } else {
      continue()
    }

  private def consume(): Unit = {
    logger.debug(s"Connection: $connId, State: ${state.toString}")
    state match {
      case State.INIT =>
        processPreamble()
      case State.PREAMBLE_COMPLETE =>
        processContentLength()
      case State.HEADER_COMPLETE =>
        processContent()
      case State.DATA_COMPLETE =>
        completeStage()
    }
  }

  override def onPull(): Unit = continue()

  override def onPush(): Unit = {
    val data = grab(in).utf8String
    logger.debug(s"Connection: $connId, State: ${state.toString}, Length: ${data.length}, Content: `$data`.")
    buffer.append(data)
    consume()
  }

  override def onUpstreamFinish(): Unit = {
    upstreamFinished = true
    logger.debug(s"""Connection: $connId, State: ${state.toString}, Upstream has finished.""")
    consume()
    if (state != State.DATA_COMPLETE) {
      throw UpstreamUnavailableException()
    }
  }

}
