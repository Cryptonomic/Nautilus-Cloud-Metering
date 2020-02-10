package tech.cryptonomic.nautilus.metering.streaming

sealed abstract class ProtocolException(msg: String = "") extends Exception(msg)

final case class InvalidPreambleException() extends ProtocolException("Invalid Preamble")

final case class InvalidVersionException() extends ProtocolException("Invalid Version")

final case class InvalidDelimiterException() extends ProtocolException("Invalid Delimiter")

final case class UpstreamUnavailableException() extends ProtocolException("Upstream stage is closed")

final case class InvalidPayloadLengthException() extends ProtocolException("Payload length is invalid")

final case class PayloadDecodingException() extends ProtocolException("Unable to decode payload")

final case class UnknownProtocolException(msg: String = "Unknown error") extends Exception(msg)