package tech.cryptonomic.nautilus.metering.routes

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes.{OK, Unauthorized}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.SourceQueueWithComplete
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.nautilus.metering.auth.{Action, AuthDecision, AuthProvider, AuthRequest}

import scala.concurrent.ExecutionContext
import scala.util.Try

class MeteringRoutes(authProvider: AuthProvider, recorder: Option[SourceQueueWithComplete[AuthDecision]])(implicit
    system: ActorSystem,
    materializer: Materializer,
    ec: ExecutionContext
) extends ErrorAccumulatingCirceSupport
    with LazyLogging {
  import akka.http.scaladsl.server.Directives._
  // List of interesting headers, the rest we can discard
  val logHeaders = List("apikey", "x-env", "x-server-name", "user-agent", "x-forwarded-for")

  val route: Route = get {
    path("authenticate") {
      extractRequest { request =>
        val headers = request.headers.map(h => (h.lowercaseName(), h.value())).toMap
        val uri = headers.getOrElse("x-original-uri", "Not Specified")
        val method = request.method.value

        val authRequest = AuthRequest(uri, method, headers)
        val decision = authProvider.authorize(authRequest)

        logger.info(
          s"Incoming Request => Action: ${decision.action}, reason: ${decision.ex} uri:$uri, method:$method, " +
            s"headers: ${headers.filter(h => logHeaders.contains(h._1.toLowerCase)).mkString(",")}"
        )

        try recorder.foreach(r => r.offer(decision))
        catch {
          case e: Exception => logger.error("Unable to record decision to database.", e)
        }

        decision.action match {
          case Action.ALLOW =>
            complete((OK, {}))
          case Action.DENY =>
            complete((Unauthorized, decision.ex.getOrElse("Unknown Error")))
        }
      }
    }
  }

}
