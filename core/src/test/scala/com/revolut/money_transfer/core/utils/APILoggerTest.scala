package com.revolut.money_transfer.core.utils

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import org.slf4j.MDC

import scala.collection.mutable.{Map => MutableMap}

class APILoggerTest extends FlatSpecLike with Matchers with BeforeAndAfterAll {

  val validConfig: Config = ConfigFactory.load("conf/sample_app_test.conf")
  val emptyConfig: Config = ConfigFactory.load("conf/empty.conf")
  val args: MutableMap[String, Any] = MutableMap(
    "runtimeKeyA" -> "runtimeValueA",
    "otherArg" -> "shouldNotBeInThreadContext"
  )

  override def afterAll(): Unit = {
    RevolutAppConfig.clear()
  }

  behavior of "configure"
  it should "have a default configuration" in {
    APILogger.configure(emptyConfig)

    assert(APILogger.constantTags.keySet().size == 0)
    assert(APILogger.runtimeTags.isEmpty)
  }

  it should "configure context tags" in {
    APILogger.configure(validConfig)

    val keySet = APILogger.constantTags.keySet()
    assert(keySet.size == 2)
    assert(keySet.contains("constantKeyA"))
    assert(keySet.contains("constantKeyB"))

    val runtimeTags = APILogger.runtimeTags
    assert(runtimeTags.size == 2)
    assert(runtimeTags.contains("runtimeKeyA"))
    assert(runtimeTags.contains("runtimeKeyB"))
  }

  behavior of "setContextTags"
  it should "set context tags" in {
    APILogger.setConstantTags()

    val constantTags = validConfig.getObject(APILogger.CONFIG_KEY + ".constant")

    APILogger.constantTags.keySet.forEach(key => {
      assert(MDC.get(key) == constantTags.get(key).render)
    })
  }

  behavior of "setRuntimeTags"
  it should "set runtime tags if present in args" in {
    APILogger.setRuntimeTags(args)

    APILogger.runtimeTags.foreach(key => {
      if (args.contains(key)) {
        assert(MDC.get(key) == args(key))
      }
    })
  }

  it should "set runtime tags to default value if not present in args" in {
    APILogger.setRuntimeTags(args)

    APILogger.runtimeTags.foreach(key => {
      if (!args.contains(key)) {
        assert(MDC.get(key) == APILogger.DEFAULT_VALUE)
      }
    })

    args.keys.foreach(key => {
      if (!APILogger.runtimeTags.contains(key)) {
          assert(MDC.get(key) == null)
      }
    })
  }
}
