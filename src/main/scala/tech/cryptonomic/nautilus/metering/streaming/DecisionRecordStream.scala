package tech.cryptonomic.nautilus.metering.streaming

import java.util.concurrent.TimeUnit

import akka.stream.OverflowStrategy
import akka.stream.alpakka.influxdb.InfluxDbWriteMessage
import akka.stream.alpakka.influxdb.scaladsl.InfluxDbSink
import akka.stream.scaladsl.{RunnableGraph, Source, SourceQueueWithComplete}
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.dto.Point
import tech.cryptonomic.nautilus.metering.auth.AuthDecision
import tech.cryptonomic.nautilus.metering.config.InfluxDbConfig

/**  The akka stream that takes each response and pushes to a event / metrics database, in this case InfluxDb.
  */
object DecisionRecordStream {

  private def makeUri(cfg: InfluxDbConfig): String =
    s"${cfg.protocol}://${cfg.host}:${cfg.port}"

  private def connect(cfg: InfluxDbConfig): InfluxDB =
    InfluxDBFactory.connect(makeUri(cfg), cfg.username, cfg.password)

  /**  Creates a runnable graph that accepts `Response` objects at its source and writes them out to InfluxDb
    *  at the end.
    *
    * @param cfg  A InfluxDb configuration
    * @return The runnable graph
    */
  def apply(cfg: InfluxDbConfig): RunnableGraph[SourceQueueWithComplete[AuthDecision]] = {
    implicit val influxDb: InfluxDB = connect(cfg)
    Source
      .queue[AuthDecision](10240, OverflowStrategy.backpressure)
      .map { x =>
        val p = Point.measurement(cfg.measurementName)

        p.addField("countable", 1)
        p.tag("decision", x.action.toString)
        p.tag("uri", x.request.uri)
        p.tag("useragent", x.request.headers.getOrElse("user-agent", "not specified"))
        p.tag("servername", x.request.headers.getOrElse("x-server-name", "not specified"))
        p.tag("ip", x.request.headers.getOrElse("x-forwarded-for", "not specified"))
        p.tag("method", x.request.method)

        p.tag("exception", x.ex.getOrElse(""))
        p.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        InfluxDbWriteMessage(p.build())
          .withDatabaseName(cfg.database)
      }
      .async
      .groupedWithin(cfg.maxBatchSize, cfg.maxBatchWait)
      .to(InfluxDbSink.create())
      .named("DecisionRecordStream")
  }
}
