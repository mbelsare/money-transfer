
package com.revolut.money_transfer.core.core.cache

import com.revolut.money_transfer.core.core.Dag
import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scalax.collection.Graph
import scalax.collection.GraphPredef._

class DagCacheTest extends FlatSpec with BeforeAndAfterAll {

  val a = "a"
  val b = "b"
  val dagName = "dag"

  val graph = Graph(a ~> b)
  val dag = new Dag(graph, dagName)

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "add the graph to cache and retrieve it" in {
    DagCache.add(dag)
    assert(DagCache.get(dag.name).isDefined)
  }

  it should "delete an item from the cache" in {
    DagCache.delete(dagName)
    assert(DagCache.get(dagName).isEmpty)
  }

}
