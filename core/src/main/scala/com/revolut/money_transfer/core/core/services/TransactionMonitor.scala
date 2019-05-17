package com.revolut.money_transfer.core.core.services

import java.util.UUID

import com.revolut.money_transfer.core.core.TASK_STATE
import com.revolut.money_transfer.core.core.cache._

object TRANSACTION_STATE extends Enumeration {

  type STATE = Value

  /*
   * A TRANSACTION has four states based on flags to DAG,
   * RUNNING = Currently Executing
   * FINISHED_SUCCESSFULLY = All Critical Plugins Finished Successfully- API should return a 200
   * FINISHED_UNSUCCESSFULLY =
   * Critical Plugins Finished UN-Successfully- API should return a 500 or 400 based on first error that gets propagated
   * TERMINATED = Any terminate_request_on_failure actor fails, return 500
   */
  val RUNNING = Value("RUNNING")
  val FINISHED_SUCCESSFULLY = Value("FINISHED_SUCCESSFULLY")
  val FINISHED_UNSUCCESSFULLY = Value("FINISHED_UNSUCCESSFULLY")
  val FINISHED_PARTIALLY_SUCCESSFUL = Value("FINISHED_PARTIALLY_SUCCESSFUL")
  val TERMINATED = Value("TERMINATED")
}

object TransactionMonitor {

  // Since getting the map of nodes returns a set, if all tasks have the same value, the set will have one element
  // So we can assume all nodes have that task state
  def state(transactionId: UUID, dagName: String): TRANSACTION_STATE.Value = {
    val taskStates = DagCache(dagName).nodes.map(node => TaskCache.getTaskState(transactionId, node))

    val isRunning = taskStates.contains(TASK_STATE.RUNNING) || taskStates.contains(TASK_STATE.DORMANT)
    val isSuccessful = taskStates.size == 1 && taskStates.head == TASK_STATE.FINISHED_SUCCESSFULLY
    val isUnsuccessful = taskStates.size == 1 && taskStates.head == TASK_STATE.FINISHED_UNSUCCESSFULLY
    val isTerminated = taskStates.contains(TASK_STATE.TERMINATED)

    taskStates match {
      case _ if isTerminated => TRANSACTION_STATE.TERMINATED
      case _ if isRunning => TRANSACTION_STATE.RUNNING
      case _ if isSuccessful => TRANSACTION_STATE.FINISHED_SUCCESSFULLY
      case _ if isUnsuccessful => TRANSACTION_STATE.FINISHED_UNSUCCESSFULLY
      case _ => TRANSACTION_STATE.FINISHED_UNSUCCESSFULLY
    }

  }

  /**
    * Clear all the caches for a given transaction and its associated DAG.
    *
    * @param transactionId
    * @param dagName
    */
  def clearCaches(transactionId: UUID, dagName: String): Unit = {
    RouteCache.delete(transactionId)
    MessageCache.delete(transactionId)
    ErrorCache.delete(transactionId)
    val dag = DagCache.get(dagName)
    if (dag.isDefined) {
      dag.get.nodes.foreach(TaskCache.delete(transactionId, _))
    }
  }
}

