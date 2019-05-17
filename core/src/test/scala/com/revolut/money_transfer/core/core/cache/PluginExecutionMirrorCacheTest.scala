
package com.revolut.money_transfer.core.core.cache

import java.io.File
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{PluginRequest, PluginStartup, RouteConfig}
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.utils._
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.reflect.runtime.universe.{InstanceMirror, Mirror, ModuleMirror, ModuleSymbol}

class PluginExecutionMirrorCacheTest extends FlatSpec with BeforeAndAfterAll {

  val configFile = "core/src/test/resources/conf/app_route.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val actors = routeConfig.actors
  val appConfig = ConfigFactory.populateConfig(
    new File("core/src/test/resources/conf/sample_app_test.conf"), routeConfig.actors)
  APILogger.configure(appConfig)
  APILogger.setConstantTags()
  val timeoutInSeconds: Int = 10
  implicit val timeout = Timeout(timeoutInSeconds, TimeUnit.SECONDS)
  implicit var system = ActorSystem(routeConfig.actor_system_name, appConfig)
  implicit val materializer = ActorMaterializer()
  val featureLibClassFile: String = "core/external_libs/revolut-plugins-test.jar"
  val classLoader = PluginLibJar.load(featureLibClassFile)
  val instanceIdentifier = this.toString()
  val scalaExecution: ScalaExecution = new ScalaExecution()

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
    PluginExecutionMirrorCache.clear()
    system.terminate()
  }

  override def beforeAll(): Unit = {
    scalaExecution.scalaInvocation(classLoader,
      "com.revolut.money_transfer.plugins.AccountOperations",
      instanceIdentifier,
      PluginStartup(RevolutAppConfig.config.get ++ ConfigCache.get("withdraw").getOrElse(Map()))
        .asInstanceOf[PluginRequest])
  }

  behavior of "PluginExecutionMirrorCache"

  it should "verify that it should have already populated PluginExecutionMirrorCache" in {
    assert(!PluginExecutionMirrorCache.isEmpty)
  }

  it should "be able to retrieve a classpaths mirror map" in {
    assert(PluginExecutionMirrorCache.getModule("com.revolut.money_transfer.plugins.AccountOperations"
      + instanceIdentifier).isDefined)
  }

  it should "be able to retrieve a classpaths mirror" in {
    assert(PluginExecutionMirrorCache.getMirror("com.revolut.money_transfer.plugins.AccountOperations"
      + instanceIdentifier).get.isInstanceOf[Mirror])
  }

  it should "be able to retrieve a classpaths module" in {
    assert(PluginExecutionMirrorCache.getModule("com.revolut.money_transfer.plugins.AccountOperations"
      + instanceIdentifier).get.isInstanceOf[ModuleSymbol])
  }

  it should "be able to retrieve a classpaths moduleMirror" in {
    assert(PluginExecutionMirrorCache.getModuleMirror("com.revolut.money_transfer.plugins.AccountOperations"
      + instanceIdentifier).get.isInstanceOf[ModuleMirror])
  }

  it should "be able to retrieve a classpaths instanceMirror" in {
    assert(PluginExecutionMirrorCache.getInstanceMirror("com.revolut.money_transfer.plugins.AccountOperations"
      + instanceIdentifier).get.isInstanceOf[InstanceMirror])
  }
}
