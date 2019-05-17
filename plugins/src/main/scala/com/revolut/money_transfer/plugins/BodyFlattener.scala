package com.revolut.money_transfer.plugins

import com.revolut.money_transfer.plugins.decorators.Skippability

import scala.collection.mutable.{Map => MutableMap}

object BodyFlattener extends Plugin with Skippability {

  override def startup(configMap: collection.Map[String, Any]): collection.Map[String, Any]= {

    Map()

  }

  /**
    * Method called when processing a given API request.
    *
    * @param argsMap
    * @param configMap
    * @return map of execution results, which will be added to MessageCache
    */
  override def execute(argsMap: collection.Map[String, Any], configMap: collection.Map[String, Any]):
  collection.Map[String, Any] = {
    logger.debug(s"${this.getClass.getSimpleName} -- on EXECUTE received argsMap with keys ${argsMap.keySet}")
    flattenInput(argsMap)
  }

  def flattenInput(argsMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    var mutableArgs = MutableMap() ++ argsMap
    argsMap.map(x => x._2 match {
      case e: Map[String, Any] => e.map(k => {
        mutableArgs(x._1 + "." + k._1) = k._2
        if ( isTypeMap(k._2) ) {
          val res = flattenInput(collection.Map(x._1 + "." + k._1 -> k._2.asInstanceOf[Map[String, Any]]))
          mutableArgs = mutableArgs ++ res
        }
      })
      case _ => mutableArgs
    })
    mutableArgs
  }

  def isTypeMap(value: Any): Boolean = {
    value match {
      case e: Map[String, Any] => true
      case _ => false
    }
  }

}
