package com.revolut.money_transfer.core.utils

import com.revolut.money_transfer.core.case_classes.RouteConfig
import org.scalatest.FlatSpecLike

class ExecutorConfigCombinerTest extends FlatSpecLike {

  val configFileShared = "core/src/test/resources/conf/app_route_dag_shared_actor.json"
  var routeConfigShared: RouteConfig = SpecReader.load[RouteConfig](configFileShared)

  it must "create one instance for each DAG that the actor is shared among if the number of " +
    "actor instances are not specified" in {
    assert(ExecutorConfigCombiner.getCombinedActorInstances(routeConfigShared.actors)("db") == 3)
  }

  it must "create the summed number of instances when an actor is shared across DAGs and the number " +
    "of actor instances is specified" in {
    assert(ExecutorConfigCombiner.getCombinedActorInstances(routeConfigShared.actors)("flattenBody") == 12)
  }

  it must "combine execution_flags keyed on dag name for the same actor names" in {

    val executionsFlagMap = ExecutorConfigCombiner.getCombinedExecutionFlags(routeConfigShared.actors)

    val dagTest1 = executionsFlagMap("db")("dagTest")
    val dagTest2 = executionsFlagMap("db")("dagTest2")

    assert(executionsFlagMap("db").nonEmpty)
    assert(dagTest1.nonEmpty)
    assert(dagTest2.nonEmpty)
    assert(dagTest1("is_skippable") == true)
    assert(dagTest1("skip_checks").asInstanceOf[Map[String,Any]]("KEY_2") == false)
    assert(dagTest2("is_skippable") == false)
    assert(dagTest2("skip_checks").asInstanceOf[Map[String,Any]]("KEY_2") == true)
  }

  it must "only combine execution_flags if specified or default empty Map" in {
    val executionFlagsMap = ExecutorConfigCombiner.getCombinedExecutionFlags(routeConfigShared.actors)
    assert(executionFlagsMap("db").size == 3)
    assert(executionFlagsMap("db")("dagTest").nonEmpty)
    assert(executionFlagsMap("db")("dagTest2").nonEmpty)
    assert(executionFlagsMap("db")("dagTest3").isEmpty)
  }

}
