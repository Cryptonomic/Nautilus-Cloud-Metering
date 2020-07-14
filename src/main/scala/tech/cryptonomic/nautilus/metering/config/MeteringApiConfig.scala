package tech.cryptonomic.nautilus.metering.config

/**
 * Configuration of the Metering API
 * @param host IP on which service will be exposed
 * @param port on which API will be exposed
 * @param keys which will be allowed to access Metering API
 */
case class MeteringApiConfig(host: String, port: Int, keys: List[String])
