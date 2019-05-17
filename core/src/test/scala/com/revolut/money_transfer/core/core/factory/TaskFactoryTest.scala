package com.revolut.money_transfer.core.core.factory

import java.util.UUID

import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.{Dag, TASK_STATE}
import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scalax.collection.Graph
import scalax.collection.GraphPredef._

class TaskFactoryTest extends FlatSpec with BeforeAndAfterAll {

  val graph = Graph("a" ~> "b", "b" ~> "c", "c" ~> "d")
  val dagName = "dag"
  val dag = new Dag(graph, dagName)
  val tid: UUID = UUID.randomUUID

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "populate the TaskCache" in {
    TaskFactory.populateTasks(dag, tid)
    dag.nodes.foreach(node => {
      assert(TaskCache.get(tid, node).isDefined)
      assert(TaskCache.getTaskState(tid, node) == TASK_STATE.DORMANT)
      TaskCache.delete(tid, node)
    })
  }


}
