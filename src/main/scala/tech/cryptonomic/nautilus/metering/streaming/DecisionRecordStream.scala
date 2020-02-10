package tech.cryptonomic.nautilus.metering.streaming

import java.util.concurrent.TimeUnit

import akka.stream.OverflowStrategy
import akka.stream.alpakka.influxdb.InfluxDbWriteMessage
import akka.stream.alpakka.influxdb.scaladsl.InfluxDbSink
import akka.stream.scaladsl.{RunnableGraph, Source, SourceQueueWithComplete}
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.influxdb.dto.Point
import tech.cryptonomic.nautilus.metering.auth.Response
import tech.cryptonomic.nautilus.metering.config.InfluxDbConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
  *  The akka stream that takes each response and pushes to a event / metrics database, in this case InfluxDb.
  */
object DecisionRecordStream {

  private def makeUri(cfg: InfluxDbConfig): String =
    s"${cfg.protocol}://${cfg.host}:${cfg.port}"

  private def connect(cfg: InfluxDbConfig): InfluxDB =
    InfluxDBFactory.connect(makeUri(cfg), cfg.username, cfg.password)

  /**
    *  Creates a runnable graph that accepts `Response` objects at its source and writes them out to InfluxDb
    *  at the end.
    *
    * @param cfg  A InfluxDb configuration
    * @return The runnable graph
    */
  def apply(cfg: InfluxDbConfig): RunnableGraph[SourceQueueWithComplete[Response]] = {
    implicit val influxDb: InfluxDB = connect(cfg)
    Source
      .queue[Response](10240, OverflowStrategy.backpressure)
      .map { x =>
        val p = Point.measurement(cfg.measurementName)
        p.addField("countable", 1)
        x.request.foreach { r =>
          p.tag("decision", x.action.toString)
          p.tag(r.headers.map(kv => (kv.name, kv.value)).toMap.asJava)
          p.tag("uri", r.uri)
          p.tag("useragent", r.userAgent)
          p.tag("servername", r.servername)
          p.tag("ip", r.ip)
        }
        p.tag("exception", x.ex.getOrElse(""))
        p.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        InfluxDbWriteMessage(p.build())
          .withDatabaseName(cfg.database)
      }
      .groupedWithin(cfg.maxBatchSize, cfg.maxBatchWait)
      .to(InfluxDbSink.create())
      .named("DecisionRecordStream")
  }
}
