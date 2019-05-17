
package com.revolut.money_transfer.core.core.services

import java.util.UUID

import akka.ConfigurationException
import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, SupervisorStrategy}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.TaskFactory
import com.revolut.money_transfer.core.core.{ROUTE_STATE, Route, TASK_STATE}
import com.revolut.money_transfer.core.utils.APILogger
import com.revolut.money_transfer.commons.StatusCodes

import scala.concurrent.duration._

class Orchestrator extends Actor {

  override def receive: Receive = {
    case msg: ExecuteWorkflow =>
      APILogger.debug(s"Received ExecuteWorkflow for ${msg.transactionId} at ${System.currentTimeMillis()}")
      val dag = DagCache(msg.dagName)
      TaskFactory.populateTasks(dag, msg.transactionId)
      RouteCache.add(new Route(msg.transactionId, sender()))
      ErrorCache.init(msg.transactionId)
      dag.startup_actors.foreach(startupActor =>
        TaskCache.updateTaskState(msg.transactionId, startupActor, TASK_STATE.FINISHED_SUCCESSFULLY))
      dag.startNodes.foreach(e =>
        context.actorSelection(TaskMonitor.actorPath) ! TaskMonitorMessage(msg.transactionId, e, msg.dagName))
    case msg: ExecutorResult =>
      APILogger.debug(s"Received ExecutorResult for ${msg.transactionId} at ${System.currentTimeMillis()}" +
        s" from ${msg.actorName}")
      TaskCache.updateTaskStateIfDefined(msg.transactionId, msg.actorName, TASK_STATE.FINISHED_SUCCESSFULLY)

      // only callNext if Route is still defined
      val route = RouteCache.get(msg.transactionId)
      if (route.isDefined) {
        callNext(msg.transactionId, msg.actorName, msg.dagName)
      }
    case msg: ExecutorError =>
      ErrorCache.appendIfDefined(msg.transactionId, msg)
      val dag = DagCache(msg.dagName)
      if (dag.terminate_request_on_failure_actors.contains(msg.actorName)) {
        TaskCache.updateTaskStateIfDefined(msg.transactionId, msg.actorName, TASK_STATE.TERMINATED)
        sink(msg.transactionId, msg.dagName)
      } else {
        TaskCache.updateTaskStateIfDefined(msg.transactionId, msg.actorName, TASK_STATE.FINISHED_UNSUCCESSFULLY)
        val route = RouteCache.get(msg.transactionId)
        if (route.isDefined) {
          callNext(msg.transactionId, msg.actorName, msg.dagName)
        }
      }
  }

  def callNext(transactionId: UUID, actorName: String, dagName: String): Unit = {
    val dag = DagCache(dagName)
    dag.successors(actorName).size match {
      case 0 => sink(transactionId, dagName)
      case _ => dag.successors(actorName).foreach(context.actorSelection(TaskMonitor.actorPath) !
        TaskMonitorMessage(transactionId, _, dagName))
    }
  }

  /**
    * Updates the TRANSACTION_STATE with the overall success of the request's dag.
    * If the DAG has multiple terminal nodes, each one does a tell to the orchestrator.
    * Only want to send one response when execution is complete, so this method acts as a terminal sync point for the
    * DAG and only sends a response to the route if all tasks are finished.
    *
    */
    private def sink(transactionId: UUID, dagName: String): Unit = {
    val route = RouteCache.get(transactionId)
    route match {
      case None =>
      case _ =>
        route.get.lock()
        route.get.state match {
          case ROUTE_STATE.INCOMPLETE =>
            val errors = ErrorCache(transactionId)
            TransactionMonitor.state(transactionId, dagName) match {
              case TRANSACTION_STATE.FINISHED_SUCCESSFULLY =>
                sendResponse(
                  WorkflowResult(transactionId, dagName),
                  s"Complete Success for transaction $transactionId at ${System.currentTimeMillis()}",
                  route.get)
              case TRANSACTION_STATE.FINISHED_UNSUCCESSFULLY =>
                sendResponse(
                  WorkflowError(errors.toString(), errors.head.statusCode, transactionId,
                    dagName),
                  s"Total Failure for transaction $transactionId: ${errors.map(_.message).mkString(", ")}",
                  route.get)
              case TRANSACTION_STATE.TERMINATED =>
                sendResponse(
                  WorkflowError(errors.toString(), StatusCodes.InternalServerError, transactionId,
                    dagName),
                  s"Terminating failure for transaction $transactionId: ${errors.map(_.message).mkString(", ")}",
                  route.get)
              case _ =>
            }
          case _ =>
        }
        route.get.unlock()
    }
  }

  /**
    * Sends a response back to the Route Generator that the transaction has finished processing
    *
    * @param response
    * @param logMessage
    * @param route
    */
  private def sendResponse(response: WorkflowResponse,
                           logMessage: String,
                           route: Route): Unit = {
    route.state = ROUTE_STATE.COMPLETE
    APILogger.info(logMessage)
    RouteCache(response.transactionId).actorRef ! response
  }
}

object Orchestrator {

  private val _name = "Orchestrator"
  private implicit val timeout = Timeout(1.second)

  def name: String = _name

  def actorPath: String = s"${Scheduler.actorPath}/$name"

  def createActor(context: ActorRefFactory, numActorInstances: Int): ActorRef = {

    val orchestrator = Props(new Orchestrator)
      .withRouter(RoundRobinPool(numActorInstances, supervisorStrategy = SupervisorStrategy.defaultStrategy))

    try {
      context.actorOf(orchestrator.withDispatcher("akka.actor.core-dispatcher"), name)
    } catch {
      case _: ConfigurationException =>
        context.actorOf(orchestrator, name)
    }
  }

}
