package com.revolut.money_transfer.core.utils

import com.revolut.money_transfer.core.case_classes.RouteConfig
import org.scalatest.WordSpec

case class TestJson(test: String, test1: Option[String], test2: Option[String])

class SpecReaderTest extends WordSpec {

  "Spec Reader" when {
    "parsing a Route file / DAG" should {
      "be able to read optional parameters" in {
        val jsonObj = SpecReader.load[TestJson]("core/src/test/resources/conf/test.json")
        assert(jsonObj.test2.getOrElse("No Value") == "No Value")
      }
    }
    "parsing single file DAG format" should {
      "be of type RouteConfig" in {
        val jsonObj = SpecReader.loadRouteConfig("core/src/test/resources/conf/app_route.json")
        assert(jsonObj.isInstanceOf[RouteConfig])
      }
    }
  }
}
