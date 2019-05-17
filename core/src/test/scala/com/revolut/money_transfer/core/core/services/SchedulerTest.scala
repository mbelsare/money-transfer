
package com.revolut.money_transfer.core.core.services

import java.io.File
import java.net.URLClassLoader

import akka.actor.{ActorSystem, Kill}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.utils.{PluginLibJar, RevolutAppConfig, SpecReader}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class SchedulerTest extends FlatSpec with BeforeAndAfterAll {

  val configFile = "core/src/test/resources/conf/api_interface_route_test.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val badConfigFile = "core/src/test/resources/conf/app_negative_route_test.json"
  val badRouteConfig: RouteConfig = SpecReader.load[RouteConfig](badConfigFile)
  val testTimeout = 200
  val pluginLibClassFile: String = "core/external_libs/revolut-plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(pluginLibClassFile)
  val appConfig = ConfigFactory.populateConfig(
    new File("core/src/test/resources/conf/sample_app_test.conf"), routeConfig.actors)
  val targetEnv = appConfig.getString("targetEnv").toLowerCase
  val configMap = appConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  private implicit val timeout = Timeout(10.seconds)
  implicit val system = ActorSystem(routeConfig.actor_system_name)
  implicit val materializer = ActorMaterializer()

  override def afterAll() {
    Scheduler.shutdown(system)
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "respond with true when all executors are successfully initialized" in {
    assert(Scheduler.startup(system, timeout, serviceConfig.toMap,
      routeConfig, classLoader).isInstanceOf[StartupResult])
    system.actorSelection(Scheduler.actorPath) ! Kill
    Thread.sleep(testTimeout)
  }

  it should "respond with false when any executors are not successfully initialized" in {
    DagCache.clear()
    assert(
      !Scheduler.startup(system, timeout, serviceConfig.toMap,
        badRouteConfig, classLoader, "SchedulerNegativeTest").isInstanceOf[StartupResult])
  }

  it should "shut down the system" in {
    Scheduler.shutdown(system)
  }

}
