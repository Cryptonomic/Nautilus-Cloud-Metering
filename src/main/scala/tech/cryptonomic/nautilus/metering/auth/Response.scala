package tech.cryptonomic.nautilus.metering.auth

import tech.cryptonomic.nautilus.metering.auth.Action.Action
import tech.cryptonomic.nautilus.metering.streaming.HttpRequest


/**
  * The response from the authorization provider
  *
  * @param action The action the client should perform
  * @param request An optional request object associated with this response
  * @param ex An optional string to explain in case the action was set to deny
  */
case class Response(action: Action, request: Option[HttpRequest], ex: Option[String])

/**
  * An enumeration for the Action a client must take post authorization check
  */
object Action extends Enumeration {
  type Action = Value
  val ALLOW = Value("Y")
  val DENY = Value("N")
}
