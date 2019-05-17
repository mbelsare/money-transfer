package com.revolut.money_transfer.plugins.decorators

import com.revolut.money_transfer.plugins.Plugin
import org.scalatest.FlatSpec

// Plugin object used for testing
class TestPlugin extends Plugin {

  override def execute(argsMap: collection.Map[String, Any],
                       configMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    throw new Exception
  }
}
class SkippabilityTest extends FlatSpec {

  // TestPlugin is located in DecoratorsTest file
  val pluginSkip = new TestPlugin with Skippability

  behavior of "test plugin with Skippability"
  it should "throw an exception when is_skippable is false" in {
    assertThrows[Exception](pluginSkip.performExecute(Map(), Map(), Map()))
    assertThrows[Exception](pluginSkip.performExecute(Map(), Map(), Map("is_skippable" -> false)))
    assertThrows[Exception](pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> false, "skip_checks" -> Map("key" -> "value"))))
    assertThrows[Exception](pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> false, "skip_checks" -> Map("key" -> "_*"))))
  }
  it should "throw an exception when is_skippable is true and there are no matching skip_checks" in {
    assertThrows[Exception](pluginSkip.performExecute(Map(), Map(), Map("is_skippable" -> true)))
    assertThrows[Exception](pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("key" -> "testValue"))))
    assertThrows[Exception](pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("testKey" -> "_*"))))
  }
  it should "not throw an exception and return an empty map when " +
    "is_skippable is true and there are matching skip_checks" in {
    assert(pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("key" -> "value"))).isEmpty)
    assert(pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("key" -> "_*"))).isEmpty)
    assert(pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("key" -> "value", "flag" -> "test"))).isEmpty)
    assert(pluginSkip.performExecute(Map("key" -> "value"), Map(),
      Map("is_skippable" -> true, "skip_checks" -> Map("key" -> "_*", "flag" -> "test"))).isEmpty)
  }
}
