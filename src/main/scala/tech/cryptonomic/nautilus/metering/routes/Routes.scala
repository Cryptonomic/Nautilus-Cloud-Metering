package tech.cryptonomic.nautilus.metering.routes
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.nautilus.metering.repositories.InfluxDbRepo

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class Routes(influxDbRepo: InfluxDbRepo)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext)
    extends ErrorAccumulatingCirceSupport
    with LazyLogging {
  import io.circe.generic.auto._
  import akka.http.scaladsl.server.Directives._

  val route: Route = get {
    concat(
      pathPrefix("queries") {
        concat(
          path("5m") {
            complete(influxDbRepo.get5minQueries)
          },
          path("24h") {
            complete(influxDbRepo.get24hQueries)
          }
        )
      },
      pathPrefix("routes") {
        concat(
          path("5m") {
            complete(influxDbRepo.get5minRoute)
          },
          path("24h") {
            complete(influxDbRepo.get24hRoute)
          }
        )
      },
      pathPrefix("ips") {
        concat(
          path("5m") {
            complete(influxDbRepo.get5minIp)
          },
          path("24h") {
            complete(influxDbRepo.get24hIp)
          }
        )
      }
    )
  }
  Http().bindAndHandle(route, "localhost", 8080).andThen {
    case Success(binding) => logger.info("Server successfully started at {}", binding.localAddress)
    case Failure(exception) => logger.error("Could not start HTTP server", exception)
  }

}
