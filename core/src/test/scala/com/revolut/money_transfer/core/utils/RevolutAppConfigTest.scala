package com.revolut.money_transfer.core.utils

import java.io.File

import com.revolut.money_transfer.core.case_classes.ActorSpec
import com.revolut.money_transfer.core.core.cache.ConfigCache
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

class RevolutAppConfigTest extends FlatSpecLike with Matchers with BeforeAndAfterAll {

  val targetEnv = "test"
  val numTestActorInstances = 1
  val actorSpecs = Array(ActorSpec("test",
    "com.test",
    numTestActorInstances,
    Map("key2" -> "value2"),
    "dagName"))

  override def afterAll: Unit = {
    RevolutAppConfig.clear()
    ConfigCache.clear()
  }

  behavior of "RevolutAppConfig"

  it should "return same instance with as it was initialized" in {
    ConfigFactory.populateConfig(new File("core/src/test/resources/conf/test_config.conf"),
      List[ActorSpec]())
    val revolutConfig = RevolutAppConfig.config.get
    assert(revolutConfig.get("test1") == Some(1))
    assert(revolutConfig.get("test2") == Some(2))
    assert(revolutConfig.get("test3") == Some("test3"))
  }

}
