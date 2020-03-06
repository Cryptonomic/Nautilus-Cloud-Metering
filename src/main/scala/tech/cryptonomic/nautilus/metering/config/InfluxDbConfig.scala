package tech.cryptonomic.nautilus.metering.config

import scala.concurrent.duration._

/**
  * Configuration options for the Influx DB client. Values are loaded from a configuration file.
  * See resource.conf for sample values.
  *
  * @param protocol The protocol to use
  * @param host The influxdb host
  * @param port The port on which influxdb is listening
  * @param maxBatchSize The maximum number of data points to cache before triggering a write operation
  * @param maxBatchWait The maximum Duration to wait before triggering a write operation
  * @param database The name of the database to write to
  * @param username The username with which to access the database
  * @param password The password with which to access the database
  * @param measurementName The measurement name to use in InfluxDb
  */
final case class InfluxDbConfig(
    protocol: String,
    host: String,
    port: Int,
    maxBatchSize: Int = 1000,
    maxBatchWait: FiniteDuration = 10 seconds,
    database: String,
    username: String,
    password: String,
    measurementName: String = "clientrequest"
)
