package tech.cryptonomic.nautilus.metering.repositories

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.github.fsanaulla.chronicler.akka.io.AkkaIOClient
import com.github.fsanaulla.chronicler.core.model.InfluxCredentials
import com.github.fsanaulla.chronicler.macros.annotations.reader.epoch
import com.github.fsanaulla.chronicler.macros.annotations.{field, timestamp}
import com.github.fsanaulla.chronicler.macros.auto._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.nautilus.metering.config.InfluxDbConfig
import tech.cryptonomic.nautilus.metering.repositories.Models.{IpCQ, QueryCQ, RouteCQ}

import scala.concurrent.{ExecutionContext, Future}

object Models {
  case class QueryCQ(@epoch @timestamp timestamp: String, @field count: Int)
  case class RouteCQ(@epoch @timestamp timestamp: String, @field count: Int, @field route: String)
  case class IpCQ(@epoch @timestamp timestamp: String, @field count: Int, @field ip: String)
}

trait InfluxDbRepo {

  /** Reads amount of queries grouped by 5minute intervals */
  def get5minQueries: Future[List[QueryCQ]]

  /** Reads amount of queries grouped by 24hour intervals */
  def get24hQueries: Future[List[QueryCQ]]

  /** Reads amount of hits per route grouped by 5minute intervals */
  def get5minRoute: Future[List[RouteCQ]]

  /** Reads amount of hits per route grouped by 24hour intervals */
  def get24hRoute: Future[List[RouteCQ]]

  /** Reads amount of hits per IP grouped by 5minute intervals */
  def get5minIp: Future[List[IpCQ]]

  /** Reads amount of hits per IP grouped by 24hour intervals */
  def get24hIp: Future[List[IpCQ]]
}

class InfluxDbRepoImpl(cfg: InfluxDbConfig)(
    implicit materializer: Materializer,
    ec: ExecutionContext,
    system: ActorSystem
) extends InfluxDbRepo
    with LazyLogging {

  val cred = InfluxCredentials(cfg.username, cfg.password)
  val client = new AkkaIOClient(cfg.host, cfg.port, Some(cred), false, None, false)

  /** Reads amount of queries grouped by 5minute intervals */
  override def get5minQueries: Future[List[QueryCQ]] =
    client
      .measurement[QueryCQ](cfg.database, "five_minute_queries_measurement")
      .read("select time, count from a_day.five_minute_queries_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of queries grouped by 24hour intervals */
  override def get24hQueries: Future[List[QueryCQ]] =
    client
      .measurement[QueryCQ](cfg.database, "daily_queries_measurement")
      .read("select time, count from a_month.daily_queries_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per route grouped by 5minute intervals */
  override def get5minRoute: Future[List[RouteCQ]] =
    client
      .measurement[RouteCQ](cfg.database, "five_minute_top_routes_measurement")
      .read("select time, count, uri from a_day.five_minute_top_routes_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per route grouped by 24hour intervals */
  override def get24hRoute: Future[List[RouteCQ]] =
    client
      .measurement[RouteCQ](cfg.database, "daily_top_routes_measurement")
      .read("select time, count, uri from a_month.daily_top_routes_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per IP grouped by 5minute intervals */
  override def get5minIp: Future[List[IpCQ]] =
    client
      .measurement[IpCQ](cfg.database, "five_minute_top_ips_measurement")
      .read("select time, count, ip from a_day.five_minute_top_ips_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per IP grouped by 24hour intervals */
  override def get24hIp: Future[List[IpCQ]] =
    client
      .measurement[IpCQ](cfg.database, "daily_top_ips_measurement")
      .read("select time, count, ip from a_month.daily_top_ips_measurement")
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }
}
