
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.case_classes.PluginError
import com.revolut.money_transfer.commons.StatusCodes
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class ErrorCacheTest extends FlatSpec with BeforeAndAfterAll {

  val tid: UUID = UUID.randomUUID()
  val tid2: UUID = UUID.randomUUID()
  val tid3: UUID = UUID.randomUUID()

  override def afterAll: Unit = {
    ErrorCache.clear()
  }

  it should "initialize the cache" in {
    ErrorCache.init(tid)
    ErrorCache(tid)
  }

  it should "add an item" in {
    ErrorCache.add(tid2, PluginError("test", "testStack", StatusCodes.BadRequest, Map()))
    assert(ErrorCache(tid2).head.message == "test")
  }

  it should "append to an entry" in {
    ErrorCache.append(tid, PluginError("test1", "testStack1", StatusCodes.BadRequest, Map()))
    ErrorCache.append(tid, PluginError("test2", "testStack2", StatusCodes.BadRequest, Map()))
    assert(ErrorCache(tid).head.message == "test1")
    assert(ErrorCache(tid).last.message == "test2")
  }

  it should "do nothing if appending to a key that isn't defined" in {
    ErrorCache.appendIfDefined(tid3, PluginError("test1", "testStack1", StatusCodes.BadRequest, Map()))
    assert(ErrorCache.get(tid3).isEmpty)
  }

}
