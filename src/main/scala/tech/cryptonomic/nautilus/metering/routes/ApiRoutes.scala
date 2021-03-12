package tech.cryptonomic.nautilus.metering.routes
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.Unauthorized
import akka.http.scaladsl.server.{Directive, Route}
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.nautilus.metering.config.MeteringApiConfig
import tech.cryptonomic.nautilus.metering.repositories.InfluxDbRepo

import scala.concurrent.ExecutionContext
import scala.util.Success

class ApiRoutes(influxDbRepo: InfluxDbRepo, apiConfig: MeteringApiConfig)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext)
    extends ErrorAccumulatingCirceSupport
    with LazyLogging {
  import io.circe.generic.auto._
  import akka.http.scaladsl.server.Directives._

  val validateApiKey: Directive[Tuple1[String]] = optionalHeaderValueByName("apikey").tflatMap[Tuple1[String]] {
    apiKeyTuple =>
      val apiKey = apiKeyTuple match {
        case Tuple1(key) => key
        case _ => None
      }
      if (apiKey.exists(k => apiConfig.keys.contains(k))) {
        provide(apiKey.getOrElse(""))
      } else {
        complete((Unauthorized, apiKey.fold("Missing API key") { _ =>
          "Incorrect API key"
        }))
      }
  }


  val route: Route = get {
    validateApiKey { _ =>
      parameters('apiKey.*) { keys =>
        parameter('from.as[Long].?) { from =>
          concat(
            pathPrefix("queries") {
              concat(
                path("5m") {
                  complete(influxDbRepo.getFiveMinuteQueries(keys.toList, from))
                },
                path("24h") {
                  complete(influxDbRepo.getDailyQueries(keys.toList, from))
                }
              )
            },
            pathPrefix("routes") {
              concat(
                path("5m") {
                  complete(influxDbRepo.getFiveMinuteRoute(keys.toList, from))
                },
                path("24h") {
                  complete(influxDbRepo.getDailyRoute(keys.toList, from))
                }
              )
            },
            pathPrefix("ips") {
              concat(
                path("5m") {
                  complete(influxDbRepo.getFiveMinuteIp(keys.toList, from))
                },
                path("24h") {
                  complete(influxDbRepo.getDailyIp(keys.toList, from))
                }
              )
            }
          )
        }
      }
    }
  }
}
