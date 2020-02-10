package tech.cryptonomic.nautilus.metering

import pureconfig._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.unixdomainsocket.scaladsl.UnixDomainSocket
import com.typesafe.scalalogging.LazyLogging
import pureconfig.loadConfig
import pureconfig.generic.auto.exportReader
import tech.cryptonomic.nautilus.metering.auth.ApiKeyAuthProvider
import tech.cryptonomic.nautilus.metering.config.{InfluxDbConfig, MeteringAgentConfig, NautilusCloudConfig}
import tech.cryptonomic.nautilus.metering.streaming.{DecisionRecordStream, IpcFlow}

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object MeteringAgent extends App {
  implicit val system: ActorSystem = ActorSystem(s"meter-agent")
  (new MeteringAgent()).start()
}

/**
  * Metering Agent IPC server
  *
  * @param cfgSrc PureConfig source. Defaults to PureConfig.default.
  * @param system Implicitly scoped Akka ActorSystem
  */
class MeteringAgent(cfgSrc: ConfigObjectSource = ConfigSource.default)(implicit system: ActorSystem)
    extends LazyLogging {
  logger.info("Nautilus Cloud Metering Agent")
  logger.info("Loading Configuration...")
  logger.debug("Debug logging is enabled")

  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionCtx: ExecutionContextExecutor = system.getDispatcher

  val dbConfig = cfgSrc.at(namespace = "nautilus.metering.database").load[InfluxDbConfig].toOption
  val agentConfig = cfgSrc.at(namespace = "nautilus.metering.agent").load[MeteringAgentConfig].toOption.get
  val nautilusCloudConfig = cfgSrc.at(namespace = "nautilus.metering.keystore").load[NautilusCloudConfig].toOption.get

  /**
    * A non blocking call to create the IPC flow and bind to the socket on which the agent can listen to requests from
    * the nginx module. Exception handling must be done by the caller.
    *
    * @return A future containing the ServerBinding
    */
  def run(): Future[UnixDomainSocket.ServerBinding] = {
    val cmQueue = dbConfig.map(cfg => DecisionRecordStream(cfg).run())
    val keyStore = new ApiKeyAuthProvider(nautilusCloudConfig)
    UnixDomainSocket().bindAndHandle(
      IpcFlow(agentConfig, keyStore, cmQueue),
      IpcFlow.socketFile(agentConfig),
      agentConfig.ipcBacklog
    )
  }

  /**
    *  Similar to the run function but blocking. Exceptions during startup are reported and will trigger the actor
    *  system to terminate.
    */
  def start(): Unit =
    Try(Await.result(run(), 10 seconds)) match {
      case Success(_) =>
        logger.info(s"Agent started on socket `${agentConfig.socketPath}`")
      case Failure(ex) =>
        logger.error("Error initializing agent.", ex)
        Await.result(system.terminate(), 10 seconds)
    }
}
