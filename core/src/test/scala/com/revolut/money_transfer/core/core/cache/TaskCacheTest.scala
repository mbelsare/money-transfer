
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.core.{TASK_STATE, Task}
import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class TaskCacheTest extends FlatSpec with BeforeAndAfterAll {

  val executorName = "test"
  val tid: UUID = UUID.randomUUID()
  val tid2: UUID = UUID.randomUUID()
  val task = new Task(tid, executorName)

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  behavior of "TaskCache"

  it should "add a task to the TaskCache" in {
    TaskCache.add(task)
    assert(TaskCache.get(tid, executorName).isDefined)
    assert(TaskCache.getTaskState(tid, executorName) == TASK_STATE.DORMANT)
  }

  it should "update a task in the TaskCache" in {
    TaskCache.updateTaskState(tid, executorName, TASK_STATE.RUNNING)
    assert(TaskCache.getTaskState(tid, executorName) == TASK_STATE.RUNNING)
  }

  it should "do nothing if updating a task for a key that isn't defined" in {
    TaskCache.updateTaskStateIfDefined(tid2, executorName, TASK_STATE.RUNNING)
    assert(TaskCache.get(tid2, executorName).isEmpty)
  }

  it should "delete a task in the TaskCache" in {
    TaskCache.delete(task)
    assert(TaskCache.get(tid, executorName).isEmpty)
    TaskCache.add(task)
    TaskCache.delete(task.transactionId, task.executorName)
    assert(TaskCache.get(tid, executorName).isEmpty)
  }

}
