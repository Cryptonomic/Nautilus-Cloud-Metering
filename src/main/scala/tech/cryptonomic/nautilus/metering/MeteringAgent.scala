package tech.cryptonomic.nautilus.metering

import pureconfig.generic.auto._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import pureconfig.ConfigSource
import tech.cryptonomic.nautilus.metering.auth.ApiKeyAuthProvider
import tech.cryptonomic.nautilus.metering.config.{InfluxDbConfig, MeteringAgentConfig, NautilusCloudConfig}
import tech.cryptonomic.nautilus.metering.routes.MeteringRoutes
import tech.cryptonomic.nautilus.metering.streaming.DecisionRecordStream

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object MeteringAgent extends App with LazyLogging {
  logger.info("Initializing metering agent")
  implicit val system: ActorSystem = ActorSystem(s"meter-agent")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionCtx: ExecutionContextExecutor = system.getDispatcher

  val dbConfig = ConfigSource.default.at(namespace = "nautilus.metering.database").load[InfluxDbConfig].toOption
  val agentConfig =
    ConfigSource.default.at(namespace = "nautilus.metering.agent").load[MeteringAgentConfig].toOption.get
  val nautilusCloudConfig =
    ConfigSource.default.at(namespace = "nautilus.metering.keystore").load[NautilusCloudConfig].toOption.get

  val keyStore = new ApiKeyAuthProvider(nautilusCloudConfig)
  val cmQueue = dbConfig.map(cfg => DecisionRecordStream(cfg).run())
  val routes = new MeteringRoutes(keyStore, cmQueue).route

  Http().bindAndHandle(routes, agentConfig.host, agentConfig.port) andThen {
    case Success(binding) => logger.info("Metering Server successfully started at {}", binding.localAddress)
    case Failure(exception) => logger.error("Could not start Metering Server", exception)
  }
}
