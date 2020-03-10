package tech.cryptonomic.nautilus.metering.routes
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.nautilus.metering.repositories.InfluxDbRepo

import scala.concurrent.ExecutionContext

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
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get5minQueries(keys.toList))
            }
          },
          path("24h") {
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get24hQueries(keys.toList))
            }
          }
        )
      },
      pathPrefix("routes") {
        concat(
          path("5m") {
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get5minRoute(keys.toList))
            }
          },
          path("24h") {
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get24hRoute(keys.toList))
            }
          }
        )
      },
      pathPrefix("ips") {
        concat(
          path("5m") {
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get5minIp(keys.toList))
            }
          },
          path("24h") {
            parameters('apiKey.*) { keys =>
              complete(influxDbRepo.get24hIp(keys.toList))
            }
          }
        )
      }
    )
  }

}
