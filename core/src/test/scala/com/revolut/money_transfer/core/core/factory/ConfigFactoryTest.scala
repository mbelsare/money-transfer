package com.revolut.money_transfer.core.core.factory

import java.io.File

import com.revolut.money_transfer.core.case_classes.RouteConfig
import com.revolut.money_transfer.core.core.cache.ConfigCache
import com.revolut.money_transfer.core.utils.{RevolutAppConfig, SpecReader}
import com.typesafe.config.Config
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FlatSpec}

class ConfigFactoryTest extends FlatSpec with BeforeAndAfter with MockitoSugar {

  var appConfig: Config = _
  val configFile = "core/src/test/resources/conf/parallel_test.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  // This uuid is used in test cases and appears in the config file,
  // so that there is no conflict with local environment variables
  val uuid = "4c0fef1e-df8d-47eb-aa19-9f0b56ef6fa8"
  val numTestActorInstances = 1

  after {
    RevolutAppConfig.clear()
    ConfigCache.clear()
  }

  it should "populate the ConfigCache and RevolutAppConfig" in {
    appConfig = ConfigFactory.populateConfig(new File("core/src/test/resources/conf/test_config.conf"),
      routeConfig.actors)

    assert(routeConfig.actors.forall(a => ConfigCache.get(a.name).isDefined))
    assert(RevolutAppConfig.config.isDefined)
  }
}

