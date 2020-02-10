package tech.cryptonomic.nautilus.metering.auth

import tech.cryptonomic.nautilus.metering.streaming.HttpRequest

/**
  * A simple trait that authorization providers can implement
  */
trait AuthProvider {

  /**
    *  Provides a response given an request to access some resource.
    *
    * @param request  The request for access by a client
    * @return A response object containing the decision.
    */
  def authorize(request: HttpRequest): Response
}
