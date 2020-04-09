package tech.cryptonomic.nautilus.metering.auth

import java.util.concurrent.CopyOnWriteArraySet

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import tech.cryptonomic.nautilus.metering.config.NautilusCloudConfig
import tech.cryptonomic.nautilus.metering.streaming

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

/**
  * An authorization provider which uses API Keys to allow / deny access to resources.
  *
  * @param cfg  The nautilus cloud configuration
  * @param system An external actor system
  */
class ApiKeyAuthProvider(cfg: NautilusCloudConfig)(implicit val system: ActorSystem)
    extends ErrorAccumulatingCirceSupport
    with AuthProvider
    with LazyLogging {

  implicit val ec: ExecutionContextExecutor = system.getDispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val keyFetchUri = s"${cfg.protocol}://${cfg.host}:${cfg.port}/apiKeys/${cfg.environment}"
  private val keySet = new CopyOnWriteArraySet[String]()

  private val cancellable = system.getScheduler.schedule(
    cfg.delay,
    cfg.interval,
    new Runnable {
      override def run(): Unit = {
        logger.info("Refreshing Keys")
        if (cfg.apiKey.nonEmpty && cfg.host.nonEmpty) {
          Http()
            .singleRequest(
              HttpRequest(uri = keyFetchUri)
                .withHeaders(RawHeader("X-Api-Key", cfg.apiKey))
            )
            .flatMap(Unmarshal(_).to[Set[String]])
            .map { s =>
              keySet.clear()
              keySet.addAll(s.asJava)
            }
            .onComplete {
              case Success(_) =>
                logger.info(s"Fetched ${keySet.size()} API keys.")
              case Failure(ex) =>
                logger.error("Failed to fetch API keys", ex)
            }
        }
      }
    }
  )

  /**
    * Handy method to shutdown the timer
    */
  def shutdown(): Unit =
    if (!cancellable.isCancelled) cancellable.cancel()

  override def authorize(request: streaming.HttpRequest): Response =
    if (request.method.toLowerCase.contentEquals("options")) {
      logger.debug("OPTIONS request, skipping auth")
      Response(Action.ALLOW, Some(request), None)
    } else {
      request.headers.find(_.name.contains("apiKey")).map(_.value) match {
        case Some(apiKey) =>
          logger.debug(
            s"Client Key : $apiKey - NC Keys: ${keySet.asScala.mkString(",")} - " +
                s"Static Keys: ${cfg.staticKeys.mkString(",")}"
          )
          if (keySet.contains(apiKey) || cfg.staticKeys.contains(apiKey))
            Response(Action.ALLOW, Some(request), None)
          else
            Response(Action.DENY, Some(request), Some("Invalid Key"))
        case None =>
          Response(Action.DENY, Some(request), Some("ApiKey header missing"))
      }
    }
}
