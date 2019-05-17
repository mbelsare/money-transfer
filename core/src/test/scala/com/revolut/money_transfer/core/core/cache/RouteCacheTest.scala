
package com.revolut.money_transfer.core.core.cache

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.core.{ROUTE_STATE, Route}
import com.revolut.money_transfer.core.utils.{RevolutAppConfig, APILogger, SpecReader}
import com.revolut.money_transfer.core.case_classes.RouteConfig
import com.revolut.money_transfer.core.core.services.Orchestrator
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class RouteCacheTest extends FlatSpec with BeforeAndAfterAll {
  val configFile = "core/src/test/resources/conf/api_interface_route_test.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val appConfig = ConfigFactory.populateConfig(
    new File("core/src/test/resources/conf/sample_app_test.conf"), routeConfig.actors)
  APILogger.configure(appConfig)
  APILogger.setConstantTags()
  implicit val system = ActorSystem("RouteCacheTestSystem", appConfig)

  val testActor = Orchestrator.createActor(system, 1)
  val tid = UUID.randomUUID()

  val route = new Route(tid, testActor)

  override def afterAll() {
    system.terminate()
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  behavior of "RouteCache"
  it should "add transactionId -> ActorRef to the cache" in {
    RouteCache.add(route)
    assert(testActor.getClass.getName == RouteCache.get(tid).get.actorRef.getClass.getName)
  }

  it should "update and get the route state" in {
    RouteCache.updateRouteState(tid, ROUTE_STATE.COMPLETE)
    assert(RouteCache.getRouteState(tid) == ROUTE_STATE.COMPLETE)
  }

}
