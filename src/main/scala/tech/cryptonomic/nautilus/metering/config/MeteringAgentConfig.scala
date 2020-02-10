package tech.cryptonomic.nautilus.metering.config

import scala.concurrent.duration._

/**
  * Configuration for the agent process
  *
  * @param socketPath The IPC socket path
  * @param ipcTimeout The protocol timeout. This controls how long the agent should wait for data before giving up.
  * @param ipcBacklog The size of the connection backlog
  */
final case class MeteringAgentConfig(
    socketPath: String,
    ipcTimeout: FiniteDuration,
    ipcBacklog: Int
)
