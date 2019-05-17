package com.revolut.money_transfer.plugins.utils

import org.scalatest.FlatSpec
import scala.collection.mutable

class InputValidationTest extends FlatSpec {

  val map = Map[String, Any]("a" -> 1, "b" -> 2)
  val argsMap = mutable.Map[String, Any]()
  val hashMap = new java.util.HashMap[String, Any]()
  hashMap.put("a", 1)
  hashMap.put("b", 2)
  val source = "test"
  val validUuid = "6345d7a9-0aa5-41e8-82bf-c9d8c2f7e997"
  val invalidUuid = "6345d7a9-0aa51222qwe-41e8asd14-82bf-c9d8c2f7e997"

  behavior of "validateMap"

  it should "not throw an exception when a map contains all required keys" in {
    InputValidation.validateMap(List("a", "b"), map, argsMap, source)
  }

  it should "throw an exception when a map does not contain all required keys" in {
    assertThrows[Exception](InputValidation.validateMap(List("a", "b", "c"), map, argsMap, source))
  }

}
