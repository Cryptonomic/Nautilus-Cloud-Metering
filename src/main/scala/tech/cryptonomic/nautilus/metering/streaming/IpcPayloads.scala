package tech.cryptonomic.nautilus.metering.streaming

/**
  * A KV container meant for storing header name and its value
  *
  * @param name The name of the header
  * @param value The value of the header
  */
case class Header(name: String, value: String)

/**
  * The request from the nginx module
  *
  * @param userAgent The agent reported to nginx in the HTTP request
  * @param uri The URI mentioned in the HTTP request
  * @param ip The IP from where the HTTP request originated from
  * @param headers A list of HTTP headers
  * @param servername The server name tag, useful for differentiating request from multiple modules
  */
case class HttpRequest(userAgent: String, uri: String, ip: String, headers: List[Header], servername: String)
