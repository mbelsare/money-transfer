package com.revolut.money_transfer.core.core.services

import akka.ConfigurationException
import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, SupervisorStrategy}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{ExecutePlugin, TaskMonitorMessage}
import com.revolut.money_transfer.core.core.TASK_STATE
import com.revolut.money_transfer.core.core.cache.{DagCache, TaskCache}
import com.revolut.money_transfer.core.core.executor.Executor
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._

/*
 * TaskMonitor is used to check status of a task's predecessors
 * Utilizes TaskCache to determine predecessor status
 * There is one TaskMonitor per Orchestrator (and one Orchestrator per request workflow/transaction)
 * TaskMonitor is called with a tell from Executor Actor
 * Responds with a tell to the Executor Actor if all predecessor tasks are complete.
 */
class TaskMonitor extends Actor with LazyLogging {

  override def receive: Receive = {
    case msg: TaskMonitorMessage => {
      val predecessorStates = DagCache(msg.dagName).predecessors(msg.actorName)
        .map(TaskCache.getTaskState(msg.transactionId, _))
      if (predecessorStates.isEmpty || predecessorStates.forall(TASK_STATE.completeStates.contains)) {
        if (TaskCache.checkAndUpdate(msg.transactionId, msg.actorName, TASK_STATE.DORMANT, TASK_STATE.RUNNING)) {
          context.actorSelection(Executor.actorPath(msg.actorName)) ! ExecutePlugin(msg.transactionId, msg.dagName)
        }
      }
    }
  }
}

object TaskMonitor {

  private val _name = "TaskMonitor"
  private implicit val timeout = Timeout(1.second)

  def name: String = _name

  def actorPath: String = s"${Scheduler.actorPath}/$name"

  def createActor(context: ActorRefFactory, numActorInstances: Int): ActorRef = {

    val taskMonitor = Props(new TaskMonitor)
      .withRouter(RoundRobinPool(numActorInstances, supervisorStrategy = SupervisorStrategy.defaultStrategy))

    try {
      context.actorOf(taskMonitor.withDispatcher("akka.actor.core-dispatcher"), TaskMonitor.name)
    } catch {
      case _: ConfigurationException =>
        context.actorOf(taskMonitor, TaskMonitor.name)
    }
  }
}
