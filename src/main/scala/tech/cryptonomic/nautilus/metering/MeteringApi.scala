package tech.cryptonomic.nautilus.metering
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.nautilus.metering.config.InfluxDbConfig
import pureconfig.ConfigSource
import tech.cryptonomic.nautilus.metering.repositories.InfluxDbRepoImpl
import tech.cryptonomic.nautilus.metering.routes.Routes
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object MeteringApi extends App with LazyLogging {
  implicit val system: ActorSystem = ActorSystem(s"meter-api")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionCtx: ExecutionContextExecutor = system.getDispatcher

  val dbConfig = ConfigSource.default.at(namespace = "nautilus.metering.database").load[InfluxDbConfig].toOption.get
  val repo = new InfluxDbRepoImpl(dbConfig)
  val routes = new Routes(repo).route

  Http().bindAndHandle(routes, "localhost", 8080) andThen {
      case Success(binding) => logger.info("Server successfully started at {}", binding.localAddress)
      case Failure(exception) => logger.error("Could not start HTTP server", exception)
    }

}