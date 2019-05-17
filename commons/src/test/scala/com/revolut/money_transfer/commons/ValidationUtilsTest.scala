package com.revolut.money_transfer.commons

import org.scalatest.FlatSpec

class ValidationUtilsTest extends FlatSpec {

  val map = Map[String, Any]("a" -> 1, "b" -> 2)

  behavior of "containsKeys"
  it should "return true when a when a map contains all keys" in {
    assert(ValidationUtils.mapContainsKeys(map, List("a", "b")))
    assert(ValidationUtils.mapContainsKeys(map, List()))
  }

  it should "return false when a when a map does not contain all keys" in {
    assert(!ValidationUtils.mapContainsKeys(map, List("a", "b", "c")))
    assert(!ValidationUtils.mapContainsKeys(map, List("c")))
  }
}
