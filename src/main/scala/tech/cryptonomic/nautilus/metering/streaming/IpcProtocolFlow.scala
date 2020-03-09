package tech.cryptonomic.nautilus.metering.streaming

import akka.stream.stage.{GraphStage, GraphStageLogic}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

/**
  * A flow whose inlet is a stream of bytes and the outlet is a HttpRequest object
  */
class IpcProtocolFlow extends GraphStage[FlowShape[ByteString, HttpRequest]] {

  val in: Inlet[ByteString] = Inlet("IPC.in")
  val out: Outlet[HttpRequest] = Outlet("IPC.out")

  override val shape: FlowShape[ByteString, HttpRequest] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new IpcProtocolLogic(shape)
}
