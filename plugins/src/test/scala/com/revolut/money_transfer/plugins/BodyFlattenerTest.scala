package com.revolut.money_transfer.plugins

import org.scalatest.FlatSpec
import scala.collection.mutable.{Map => MutableMap}

class BodyFlattenerTest extends FlatSpec {

  val configMap = MutableMap[String, Any](
    "accountKey" -> Set("body.account.depositAccountId")
  )

  val argsMap = MutableMap[String, Any](
    "body.account" -> Map("depositAccountId" -> "1234")
  )

  it should "return flattened argsMap" in {
    val map = BodyFlattener.flattenInput(argsMap)
    assert(map.isDefinedAt("body.account.depositAccountId"))
  }

}
