
package com.revolut.money_transfer.core.core.factory

import java.io.File
import java.net.URLClassLoader

import akka.actor.ActorSystem
import com.revolut.money_transfer.core.case_classes.RouteConfig
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.utils.{PluginLibJar, RevolutAppConfig, SpecReader}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec}

class DagFactoryTest extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter {

  val configFile = "core/src/test/resources/conf/app_route_dag.json"
  val jsonConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val configFileSingleNode = "core/src/test/resources/conf/app_route_dag_single_node.json"
  val jsonConfigSingleNode: RouteConfig = SpecReader.load[RouteConfig](configFileSingleNode)
  val configFileBadEdge = "core/src/test/resources/conf/app_route_dag_bad_edge.json"
  val jsonConfigBadEdge: RouteConfig = SpecReader.load[RouteConfig](configFileBadEdge)
  val configFileDuplicateName = "core/src/test/resources/conf/app_route_dag_duplicate_name.json"
  val jsonConfigDuplicateName: RouteConfig = SpecReader.load[RouteConfig](configFileDuplicateName)
  val configFileStartupOnlyInEdges = "core/src/test/resources/conf/app_route_startup_only_in_edges.json"
  val jsonConfigStartupOnlyInEdges: RouteConfig = SpecReader.load[RouteConfig](configFileStartupOnlyInEdges)

  val appConfig = ConfigFactory.populateConfig(new File("core/src/test/resources/conf/sample_app_test.conf"),
    jsonConfig.actors)
  val featureLibClassFile: String = "core/external_libs/revolut-plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(featureLibClassFile)

  var system: ActorSystem = _

  after {
    system.terminate()
  }

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "populate dependency actors as provided in the json config" in {
    system = ActorSystem(jsonConfig.actor_system_name, appConfig)
    DagFactory.initializeDags(jsonConfig.dags)
    val dag = DagCache(jsonConfig.dags.head.name)
    var node = dag.startNodes.head
    assert(node == "withdraw")
    node = dag.successors(node).head
    assert(node == "deposit")
    node = dag.successors(node).head
    assert(node == "db")
    DagCache.delete(dag.name)
  }

  it should "correctly handle a dag with no edges" in {
    system = ActorSystem(jsonConfigSingleNode.actor_system_name, appConfig)
    DagFactory.initializeDags(jsonConfigSingleNode.dags)
    val dag = DagCache(jsonConfigSingleNode.dags.head.name)
    val node = dag.startNodes.head
    assert(node == "flattenBody")
    DagCache.delete(dag.name)
  }

  it should "return false if the graph contains an invalid edge" in {
    system = ActorSystem(jsonConfigBadEdge.actor_system_name, appConfig)
    assert(!DagFactory.initializeDags(jsonConfigBadEdge.dags))
  }

  it should "return false if the graph contains a duplicate actor name" in {
    system = ActorSystem(jsonConfigDuplicateName.actor_system_name, appConfig)
    assert(!DagFactory.initializeDags(jsonConfigDuplicateName.dags))
  }

  it should "return false if the graph contains actors that are startup_only actors" in {
    system = ActorSystem(jsonConfigStartupOnlyInEdges.actor_system_name, appConfig)
    assert(!DagFactory.initializeDags(jsonConfigStartupOnlyInEdges.dags))
  }

}
