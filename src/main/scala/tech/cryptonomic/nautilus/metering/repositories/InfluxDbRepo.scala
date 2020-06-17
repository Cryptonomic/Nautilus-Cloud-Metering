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
  case class QueryCQ(@epoch @timestamp time: String, @field count: Int, @field apiKey: Option[String])
  case class RouteCQ(
      @epoch @timestamp time: String,
      @field count: Int,
      @field uri: String,
      @field apiKey: Option[String]
  )
  case class IpCQ(@epoch @timestamp time: String, @field count: Int, @field ip: String, @field apiKey: Option[String])
}

trait InfluxDbRepo {

  /** Reads amount of queries grouped by 5minute intervals */
  def getFiveMinutesQueries(apiKeys: List[String], from: Option[Long]): Future[List[QueryCQ]]

  /** Reads amount of queries grouped by 24hour intervals */
  def getDailyQueries(apiKeys: List[String], from: Option[Long]): Future[List[QueryCQ]]

  /** Reads amount of hits per route grouped by 5minute intervals */
  def getFiveMinuesRoute(apiKeys: List[String], from: Option[Long]): Future[List[RouteCQ]]

  /** Reads amount of hits per route grouped by 24hour intervals */
  def getDailyRoute(apiKeys: List[String], from: Option[Long]): Future[List[RouteCQ]]

  /** Reads amount of hits per IP grouped by 5minute intervals */
  def getFiveMinuteIp(apiKeys: List[String], from: Option[Long]): Future[List[IpCQ]]

  /** Reads amount of hits per IP grouped by 24hour intervals */
  def getDailyIp(apiKeys: List[String], from: Option[Long]): Future[List[IpCQ]]
}

class InfluxDbRepoImpl(cfg: InfluxDbConfig)(
    implicit materializer: Materializer,
    ec: ExecutionContext,
    system: ActorSystem
) extends InfluxDbRepo
    with LazyLogging {

  private val cred = InfluxCredentials(cfg.username, cfg.password)
  private val client = new AkkaIOClient(cfg.host, cfg.port, Some(cred), false, None, false)

  // Influx uses nanoseconds, but we would want to use seconds for simplicity
  private val timeConstraint: Option[Long] => String = _.map(from => s"and time > ${from * 1000000000}").getOrElse("")

  /** Reads amount of queries grouped by 5minute intervals
    * @param apiKeys list of API keys
    * @param from optional timestamp in seconds
    */
  override def getFiveMinutesQueries(apiKeys: List[String], from: Option[Long]): Future[List[QueryCQ]] =
    client
      .measurement[QueryCQ](cfg.database, "five_minute_queries_measurement")
      .read(
        s"select time, apiKey, count from a_day.five_minute_queries_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of queries grouped by 24hour intervals
    * @param apiKeys list of API keys
    * @param from optional timestamp in seconds
    */
  override def getDailyQueries(apiKeys: List[String], from: Option[Long]): Future[List[QueryCQ]] =
    client
      .measurement[QueryCQ](cfg.database, "daily_queries_measurement")
      .read(
        s"select time, apiKey, count from a_month.daily_queries_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per route grouped by 5minute intervals
    * @param apiKeys list of API keys
    * @param from optional timestamp in seconds
    */
  override def getFiveMinuesRoute(apiKeys: List[String], from: Option[Long]): Future[List[RouteCQ]] =
    client
      .measurement[RouteCQ](cfg.database, "five_minute_top_routes_measurement")
      .read(
        s"select time, apiKey, count, uri from a_day.five_minute_top_routes_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per route grouped by 24hour intervals */
  override def getDailyRoute(apiKeys: List[String], from: Option[Long]): Future[List[RouteCQ]] =
    client
      .measurement[RouteCQ](cfg.database, "daily_top_routes_measurement")
      .read(
        s"select time, apiKey, count, uri from a_month.daily_top_routes_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per IP grouped by 5minute intervals
    * @param apiKeys list of API keys
    * @param from optional timestamp in seconds
    */
  override def getFiveMinuteIp(apiKeys: List[String], from: Option[Long]): Future[List[IpCQ]] =
    client
      .measurement[IpCQ](cfg.database, "five_minute_top_ips_measurement")
      .read(
        s"select time, apiKey, count, ip from a_day.five_minute_top_ips_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }

  /** Reads amount of hits per IP grouped by 24hour intervals
    * @param apiKeys list of API keys
    * @param from optional timestamp in seconds
    */
  override def getDailyIp(apiKeys: List[String], from: Option[Long]): Future[List[IpCQ]] =
    client
      .measurement[IpCQ](cfg.database, "daily_top_ips_measurement")
      .read(
        s"select time, apiKey, count, ip from a_month.daily_top_ips_measurement where apiKey =~ /${apiKeys
          .mkString("|")}/ ${timeConstraint(from)}"
      )
      .map {
        case Left(e) =>
          logger.error("Error while executing a query", e)
          List.empty
        case Right(value) => value.toList
      }
}
