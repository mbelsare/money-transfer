package com.revolut.money_transfer.commons

/**
  * Enum to represent HTTP Status codes.
  */

// $COVERAGE-OFF$
object StatusCodes extends Enumeration {
  val OK = 200
  val Created = 201
  val BadRequest = 400
  val Forbidden = 403
  val NotFound = 404
  val InternalServerError = 500
  val GatewayTimeout = 504
}
