
package com.revolut.money_transfer.core.core.cache

import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class ConfigCacheTest extends FlatSpec with BeforeAndAfterAll {

  val name = "a"
  val configMap: Map[String, Any] = Map("b" -> "c")

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "add an item to the cache" in {
    ConfigCache.add(name, configMap)
    assert(ConfigCache.contains(name))
  }

  it should "append an item to the cache if the key exists" in {
    ConfigCache.append(name, Map("d" -> "e"))
    assert(ConfigCache(name).contains("d"))
  }

}
