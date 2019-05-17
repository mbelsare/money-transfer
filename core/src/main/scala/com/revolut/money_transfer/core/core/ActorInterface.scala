package com.revolut.money_transfer.core.core

import akka.actor.ActorSystem
import akka.util.Timeout

import scala.concurrent.ExecutionContext

trait ActorInterface {
  implicit val system: ActorSystem
  implicit val timeout: Timeout
  implicit val ec: ExecutionContext
}
