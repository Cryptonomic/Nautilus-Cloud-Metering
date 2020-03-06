package tech.cryptonomic.nautilus.metering.streaming

import java.io.File

import akka.stream.scaladsl.{Flow, SourceQueueWithComplete}
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.nautilus.metering.auth.{Action, AuthProvider, Response}
import tech.cryptonomic.nautilus.metering.config.MeteringAgentConfig

/**
  * The main IPC flow for the agent server
  */
object IpcFlow extends LazyLogging {

  /**
    * Creates a new file to be use as a unix domain socket
    *
    * @param cfg  The agent configuration
    * @return A java File object
    */
  def socketFile(cfg: MeteringAgentConfig) = {
    val file: File = new File(cfg.socketPath)
    file.delete()
    file.deleteOnExit()
    file.setWritable(true, false)
    file
  }

  /**
    *  Creates the IPC flow that joins to the socket flow
    *
    * @param cfg  The agent configuration
    * @param authProvider The authorization provider instance
    * @param recordQueue  The decision recording queue
    * @return The ipc flow
    */
  def apply(
      cfg: MeteringAgentConfig,
      authProvider: AuthProvider,
      recordQueue: Option[SourceQueueWithComplete[Response]]
  ) = {
    var ipcFlow = Flow[ByteString]
      .takeWithin(cfg.ipcTimeout)
      .via(new IpcProtocolFlow)
      .map(authProvider.authorize)
      .recover {
        case x: ProtocolException =>
          logger.error("Protocol exception occurred, Denying request", x)
          Response(Action.DENY, None, Some(x.getMessage))
        case t: Throwable =>
          logger.error("Unknown exception occurred, Denying request", t)
          Response(Action.DENY, None, Some(t.getMessage))
      }

    ipcFlow = recordQueue.map { cmq =>
      logger.info("Attaching InfluxDB Flow...")
      ipcFlow.wireTap(x => cmq.offer(x))
    }.getOrElse(ipcFlow)

    ipcFlow.map { authResponse =>
      logger.info(s"Auth response was : Ex => ${authResponse.ex} Action => ${authResponse.action} " +
        s"on Request => ${authResponse.request}")
      ByteString(authResponse.action.toString)
    }
  }

}
