package com.revolut.money_transfer.plugins

import com.revolut.money_transfer.commons.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import scalaj.http.HttpStatusException

// $COVERAGE-OFF$
trait Plugin extends LazyLogging {

  /**
    * Setup code run on initialization of the plugin.
    *
    * @param configMap
    * @return map containing any new configuration info if necessary, which will be added to ConfigCache
    */
  @throws(classOf[HttpStatusException])
  def startup(configMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    logger.debug(s"${this.getClass.getSimpleName} -- on STARTUP received config: ${configMap.keySet}")
    Map()
  }

  /**
    * Method called when processing a given API request.
    *
    * @param argsMap
    * @param configMap
    * @return map of execution results, which will be added to MessageCache
    */
  @throws(classOf[HttpStatusException])
  def execute(argsMap: collection.Map[String, Any], configMap: collection.Map[String, Any]): collection.Map[String, Any]

  /**
    * The execution method that is called by 'ScalaExecution' itself to control plugin traits.
    *
    * @param argsMap
    * @param configMap
    * @param executionFlags
    * @return map of execution results
    */
  @throws(classOf[HttpStatusException])
  def performExecute(argsMap: collection.Map[String, Any],
                     configMap: collection.Map[String, Any],
                     executionFlags: collection.Map[String, Any]): collection.Map[String, Any] = {

      execute(argsMap, configMap)
  }

  /**
    * Method called on shutdown
    *
    * @param configMap
    * @return map containing info on shutdown state or empty map
    */
  @throws(classOf[HttpStatusException])
  def shutdown(configMap: collection.Map[String, Any]): collection.Map[String, Any] = Map()

  /**
    * Method called to check the health of a given plugin
    *
    * @param configMap
    * @return
    */
  def healthCheck(configMap: collection.Map[String, Any]): Int = {
    StatusCodes.OK
  }

}
