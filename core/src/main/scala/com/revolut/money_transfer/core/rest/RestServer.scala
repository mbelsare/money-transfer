
package com.revolut.money_transfer.core.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

import scala.concurrent.Future

class RestServer(implicit val system: ActorSystem,
                 implicit val materializer: ActorMaterializer) {
  implicit val executionContext = system.dispatcher
  var bindingFuture: Future[Http.ServerBinding] = _

  def start(host: String, port: Int, route: Route): Unit =
    bindingFuture = Http().bindAndHandle(route, host, port)

  def stop(): Unit = bindingFuture.flatMap(_.unbind())

}
