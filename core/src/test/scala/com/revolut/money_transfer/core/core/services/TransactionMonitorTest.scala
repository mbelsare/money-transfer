package com.revolut.money_transfer.core.core.services

import java.util.UUID

import akka.actor.ActorRef
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.{Dag, Route, TASK_STATE, Task}
import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.mutable.{Map => MutableMap}
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge

class TransactionMonitorTest extends FlatSpec with BeforeAndAfterAll {

  val tid = UUID.randomUUID()
  val task1 = new Task(tid, "task1")
  val task2 = new Task(tid, "task2")
  val task3 = new Task(tid, "task3")
  val dagName = "dag"
  val graph = Graph[String, DiEdge]("task1", "task2", "task3")
  val dag = new Dag(graph, dagName)
  val invalidTid = UUID.randomUUID()
  val invalidDagName = "dorg"

  DagCache.add(dag)
  TaskCache.add(task1)
  TaskCache.add(task2)
  TaskCache.add(task3)

  val actorRef: ActorRef = ActorRef.noSender
  val route = new Route(tid, actorRef)
  val argsMap: MutableMap[String, Any] = MutableMap()
  RouteCache.add(route)
  MessageCache.add(tid, argsMap)

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  behavior of "TransactionMonitor"

  it should "return running if all tasks are running" in {
    TaskCache.updateTaskState(tid, "task1", TASK_STATE.RUNNING)
    TaskCache.updateTaskState(tid, "task2", TASK_STATE.RUNNING)
    TaskCache.updateTaskState(tid, "task3", TASK_STATE.RUNNING)
    assert(TransactionMonitor.state(tid, dagName) == TRANSACTION_STATE.RUNNING)
  }

  it should "return running if any tasks are not complete" in {
    TaskCache.updateTaskState(tid, "task1", TASK_STATE.DORMANT)
    TaskCache.updateTaskState(tid, "task2", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task3", TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(TransactionMonitor.state(tid, dagName) == TRANSACTION_STATE.RUNNING)
  }

  it should "return successful if all tasks are successful" in {
    TaskCache.updateTaskState(tid, "task1", TASK_STATE.FINISHED_SUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task2", TASK_STATE.FINISHED_SUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task3", TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(TransactionMonitor.state(tid, dagName) == TRANSACTION_STATE.FINISHED_SUCCESSFULLY)
  }

  it should "return unsuccessful if all tasks are unsuccessful or plugins error" in {
    TaskCache.updateTaskState(tid, "task1", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task2", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task3", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    assert(TransactionMonitor.state(tid, dagName) == TRANSACTION_STATE.FINISHED_UNSUCCESSFULLY)
  }

  it should "return unsuccessful if tasks are successful and unsuccessful" in {
    TaskCache.updateTaskState(tid, "task1", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task2", TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, "task3", TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(TransactionMonitor.state(tid, dagName) == TRANSACTION_STATE.FINISHED_UNSUCCESSFULLY)
  }

  it should "clear transaction-based caches successfully" in {
    TransactionMonitor.clearCaches(tid, dagName)
    assert(TaskCache.isEmpty)
    assert(RouteCache.isEmpty)
    assert(MessageCache.isEmpty)
    assert(!DagCache.isEmpty)
  }

  it should "only clear task cache with valid transaction ID and Dag ID" in {
    TaskCache.add(task1)
    TransactionMonitor.clearCaches(invalidTid, dagName)
    assert(!TaskCache.isEmpty)

    TransactionMonitor.clearCaches(tid, invalidDagName)
    assert(!TaskCache.isEmpty)

    TransactionMonitor.clearCaches(invalidTid, invalidDagName)
    assert(!TaskCache.isEmpty)
  }
}
