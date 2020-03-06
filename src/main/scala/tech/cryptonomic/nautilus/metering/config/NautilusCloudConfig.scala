package tech.cryptonomic.nautilus.metering.config

import scala.concurrent.duration._

/**
  * Configuration to enable the agent process to talk to the Nautilus cloud server
  *
  * @param protocol The protocol to use, usually HTTPS
  * @param host The host running nautilus cloud
  * @param port The port on which nautilus cloud is available on
  * @param environment  The environment setting i.e prod, dev, etc.
  * @param apiKey The access key, this is privileged and should be kept a secret i.e do not commit
  * @param delay  The initial delay duration before first attempting to talk to nautilus cloud
  * @param interval The interval duration before periodic fetches of data from nautilus cloud
  * @param staticKeys A list of hard coded keys
  */
final case class NautilusCloudConfig(
    protocol: String,
    host: String,
    port: Int,
    environment: String,
    apiKey: String,
    delay: FiniteDuration,
    interval: FiniteDuration,
    staticKeys: List[String]
)
