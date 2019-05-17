package com.revolut.money_transfer.core.core.executor

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.CreateExecutors
import com.revolut.money_transfer.core.core.services.Scheduler
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

class ExecutorStartupHandler extends Actor with LazyLogging {

  override def receive: Receive = {
    case msg: CreateExecutors => {
      Executor.createActors(
        msg.context,
        msg.actors,
        msg.pluginLibClassLoader,
        msg.executorStartupDelay,
        msg.executorDispatcherType)
    }
  }
}

object ExecutorStartupHandler {

  private val _name = "ExecutorStartupHandler"
  private implicit val timeout = Timeout(1.second)

  def name: String = _name

  def actorPath: String = s"${Scheduler.actorPath}/$name"

  def createActor(context: ActorRefFactory): ActorRef = {
    context.actorOf(Props(new ExecutorStartupHandler), name)
  }

}
