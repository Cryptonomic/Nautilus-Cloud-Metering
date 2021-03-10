package tech.cryptonomic.nautilus.metering.auth

import tech.cryptonomic.nautilus.metering.auth.Action.Action

/** A simple trait that authorization providers can implement
  */
trait AuthProvider {

  /**  Provides a response given an request to access some resource.
    *
    * @param request The user's request
    * @return A response object containing the decision.
    */
  def authorize(request: AuthRequest): AuthDecision
}

/** The decision from the authorization provider
  *
  * @param action The action the client should perform
  * @param ex An optional string to explain in case the action was set to deny
  * @param request The request which was used as input for this decision
  */
case class AuthDecision(action: Action, ex: Option[String], request: AuthRequest)

/** The request from the user
  * @param uri The resource the user is trying to access
  * @param method The method used to access the resource
  * @param headers  The headers with the request
  */
case class AuthRequest(uri: String, method: String, headers: Map[String, String])

/** An enumeration for the Action a client must take post authorization check
  */
object Action extends Enumeration {
  type Action = Value
  val ALLOW = Value("Y")
  val DENY = Value("N")
}
