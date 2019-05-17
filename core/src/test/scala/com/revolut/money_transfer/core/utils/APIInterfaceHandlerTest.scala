package com.revolut.money_transfer.core.utils

import java.io.File
import java.util.UUID

import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{ExecutorError, RouteConfig}
import com.revolut.money_transfer.core.core.cache.MessageCache
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import org.scalatest.FlatSpecLike

import scala.concurrent.duration._

class APIInterfaceHandlerTest extends FlatSpecLike {

  val aPIInterfaceHandlerTest = new APIInterfaceHandler()
  val configFile = "core/src/test/resources/conf/api_interface_route_test.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val transactionId = "61474032-00ab-418c-898d-94a3b2b9132e"
  val responseMap = Map("accountId" -> 101,
                        "body" -> Map("some" -> "JSON source", "a" -> "b" ),
                        "transactionId" -> "e21071d4-b3db-4bea-b36b-bdeac5064838")

  val responseMapWithErrors = Map("accountId" -> 101,
    "body" -> Map("some" -> "JSON source", "a" -> "b" ),
    "transactionId" -> "e21071d4-b3db-4bea-b36b-bdeac5064838",
    "errors" -> List(ExecutorError("scalaj.http.HttpStatusException: 500 Error:" +
      "BodyFlattener$: Received Internal Server Error from API",
      500, "flattenBody",
      UUID.fromString(transactionId),
      "dagname"))
    )

  val config = ConfigFactory.populateConfig(new File("core/src/test/resources/conf/sample_app_test.conf"),
    routeConfig.actors)
  APILogger.configure(config)
  APILogger.setConstantTags()
  private implicit val timeout = Timeout(1.second)
  APILogger.configure(config)
  APILogger.setConstantTags()

  behavior of "API Interface Handler"
  it should "return keys for the map from response json" in {
    MessageCache.add(UUID.fromString(transactionId), responseMap)
    val response = aPIInterfaceHandlerTest.sendResponse(UUID.fromString(transactionId),
      routeConfig.routes.head.default_dag, 200, System.currentTimeMillis(), responseMap, routeConfig)

    assert(response.toString.contains("accountId"))
    assert(!response.toString.contains("transactionId"))
  }

  it should "not throw an exception and return errors map" in {
    MessageCache.add(UUID.fromString(transactionId), responseMapWithErrors)
    val response = aPIInterfaceHandlerTest.sendResponse(UUID.fromString(transactionId),
        "dagname", 203, System.currentTimeMillis(), responseMapWithErrors, routeConfig)
    assert(response.status.intValue == 203)
    assert(response.toString.contains("errors"))
    assert(response.toString.contains("accountId"))
  }
}
