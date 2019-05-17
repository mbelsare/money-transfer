
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.core.{TASK_STATE, Task}

/*
 * Cache of individual task state
 * Also stores task ID (unique to a specific task's workflow) and executor name (unique key for node in DAG)
 * Populated by Orchestrator when beginning execution of a workflow
 */
object TaskCache extends Cache[(UUID, String), Task] {

  @inline
  def add(task: Task): Unit = super.add((task.transactionId, task.executorName), task)

  @inline
  def getTaskState(transactionId: UUID, executorName: String): TASK_STATE.Value =
    get(transactionId, executorName).get.state

  @inline
  def updateTaskState(transactionId: UUID, executorName: String, taskState: TASK_STATE.Value): Unit =
    this(transactionId, executorName).state = taskState

  @inline
  def updateTaskStateIfDefined(transactionId: UUID, executorName: String, taskState: TASK_STATE.Value): Unit =
    if (get(transactionId, executorName).isDefined) {
      updateTaskState(transactionId, executorName, taskState)
    }

  def checkAndUpdate(transactionId: UUID,
                     executorName: String,
                     currentState: TASK_STATE.Value,
                     taskState: TASK_STATE.Value): Boolean = {
    apply(transactionId, executorName).lock()
    if (getTaskState(transactionId, executorName) == currentState) {
      updateTaskState(transactionId, executorName, taskState)
      apply(transactionId, executorName).unlock()
      true
    } else {
      apply(transactionId, executorName).unlock()
      false
    }
  }

  @inline
  def delete(task: Task): Unit = super.delete((task.transactionId, task.executorName))

  @inline
  def delete(transactionId: UUID, executorName: String): Unit = super.delete((transactionId, executorName))

}
