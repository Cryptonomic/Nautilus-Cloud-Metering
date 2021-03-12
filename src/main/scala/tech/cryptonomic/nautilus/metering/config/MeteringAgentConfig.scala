package tech.cryptonomic.nautilus.metering.config

/** Configuration for the agent process
  *
  * @param host The host running the agent
  * @param port The port on which the agent should run
  */
final case class MeteringAgentConfig(host: String, port: Int)
