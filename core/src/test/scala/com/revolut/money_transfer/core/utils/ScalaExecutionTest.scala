package com.revolut.money_transfer.core.utils

import java.net.URLClassLoader

import com.revolut.money_transfer.core.case_classes.{PluginError, PluginExecute, PluginStartup}
import org.scalatest.FlatSpec

import scala.collection.mutable.{Map => MutableMap}

class ScalaExecutionTest extends FlatSpec {

  val pluginLibClassFile = "core/external_libs/revolut-plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(pluginLibClassFile)
  val initArgs: MutableMap[String, Any] = MutableMap()
  val className = "com.revolut.money_transfer.plugins.BodyFlattener"
  val instanceIdentifier = this.toString()
  val scalaExecution: ScalaExecution = new ScalaExecution()

  behavior of "ScalaExecution"

  it should "invoke actor startup and return a PluginResponse" in {
    assert(scalaExecution.scalaInvocation(classLoader, className, instanceIdentifier,
      PluginStartup(Map())).result == Map())
  }

  it should "invoke actor execute and return a PluginResponse" in {
    assert(scalaExecution.scalaInvocation(
      classLoader,
      className,
      instanceIdentifier,
      PluginExecute(Map("key" -> "value"), Map(), Map())).result("key") == "value")
  }

  it should "handle exceptions" in {
    assert(scalaExecution.scalaInvocation(classLoader, "badActor", instanceIdentifier,
      PluginStartup(Map())).isInstanceOf[PluginError])
  }
}
