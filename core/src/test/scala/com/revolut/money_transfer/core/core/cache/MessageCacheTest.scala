
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.utils.RevolutAppConfig
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.mutable.{Map => MutableMap}

class MessageCacheTest extends FlatSpec with BeforeAndAfterAll {

  val tid: UUID = UUID.randomUUID()
  val tid2: UUID = UUID.randomUUID()

  override def afterAll(): Unit = {
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  behavior of "MessageCache"

  it should "Add value to the cache" in {
    val argsMap: MutableMap[String, Any] = MutableMap[String, Any]("testKey" -> "testVal")
    MessageCache.add(tid, argsMap)

    assert(MessageCache.get(tid).isDefined)
    assert(MessageCache(tid)("testKey") == "testVal")
  }

  it should "append the message cache" in {
    val argsMap: MutableMap[String, Any] = MutableMap[String, Any]("newTestKey" -> "newTestVal")
    MessageCache.append(tid, argsMap)

    assert(MessageCache.get(tid).isDefined)
    assert(MessageCache.get(tid).get("testKey") == "testVal")
    assert(MessageCache.get(tid).get("newTestKey") == "newTestVal")
  }

  it should "do nothing if appending to a key that isn't defined" in {
    val argsMap: MutableMap[String, Any] = MutableMap[String, Any]("testKey" -> "testVal")
    MessageCache.appendIfDefined(tid2, argsMap)

    assert(MessageCache.get(tid2).isEmpty)
  }

  it should "delete a message" in {
    MessageCache.delete(tid)
    assert(MessageCache.get(tid).isEmpty)
  }

}
