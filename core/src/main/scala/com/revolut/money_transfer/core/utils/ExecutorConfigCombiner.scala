package com.revolut.money_transfer.core.utils

import com.revolut.money_transfer.core.case_classes.ActorSpec
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

object ExecutorConfigCombiner extends LazyLogging {
  /**
    * Combines number of instances per actor grouped by actor name
    * @param actorSpecs
    * @return
    */
  def getCombinedActorInstances(actorSpecs: List[ActorSpec]): mutable.Map[String, Int] = {
    var actorInstancesMap = mutable.Map[String,Int]()
    actorSpecs.foreach(a => {
      if (actorInstancesMap.contains(a.name)) {
        actorInstancesMap(a.name) += a.numActorInstances
      } else {
        actorInstancesMap(a.name) = a.numActorInstances
      }
    })
    actorInstancesMap
  }

  /**
    * Merges config maps for execution_flags grouped by actor name,
    * with each execution_flags map keyed by dag name
    * @param actors
    * @return
    */
  def getCombinedExecutionFlags(actors: List[ActorSpec]):
  mutable.Map[String, Map[String, collection.Map[String, Any]]] = {
    var executionFlagsMap = mutable.Map[String, Map[String, collection.Map[String, Any]]]()

    actors.foreach(a => {
      val dagName = a.dagName
      val executionFlags = a.execution_flags
      if (executionFlagsMap.contains(a.name)) {
        executionFlagsMap(a.name) += (dagName -> executionFlags)
      } else {
        executionFlagsMap(a.name) = Map[String, collection.Map[String, Any]](dagName -> executionFlags)
      }
    })
    executionFlagsMap
  }

}
