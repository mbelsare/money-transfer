package com.revolut.money_transfer.plugins.decorators

import com.revolut.money_transfer.plugins.Plugin
import scalaj.http.HttpStatusException

/*
 * Allows for plugins to be marked as skippable. If any of a plugin's 'skip_checks' key/value pairs are found in the
 * argsMap at the start of execution, the execution fo the plugin will be a skipped and an empty map will be returned
 * instead. An optional value of '_*' can be used in the 'skip_checks' to match for any matching key in the argsMap.
 */
trait Skippability extends Plugin {

  /**
    * The execution method that handles the skippability of a plugin
    *
    * @param argsMap
    * @param configMap
    * @param executionFlags
    * @return map of execution results
    */
  @throws(classOf[HttpStatusException])
  override def performExecute(argsMap: collection.Map[String, Any],
                              configMap: collection.Map[String, Any],
                              executionFlags: collection.Map[String, Any]): collection.Map[String, Any] = {

    val isSkippable = executionFlags.getOrElse("is_skippable", false).asInstanceOf[Boolean]

    if (isSkippable) {
      val skipMap = executionFlags.getOrElse("skip_checks",
        collection.Map[String, Any]()).asInstanceOf[collection.Map[String, Any]]
      if(skipMap.isEmpty) {
        logger.warn(s"For plugin ${this.getClass.getSimpleName}, is_skippable is set to True but mappings were not " +
          s"provided correctly. Please resolve.")
      }
      val matchedTags = skipMap.filter {
        case(key:String, value:Any) => {
          if (value.asInstanceOf[String] == "_*" && argsMap.get(key).isDefined) true
          else if (argsMap.get(key).isDefined && argsMap.get(key).contains(value)) true
          else false
        }
      }
      if (matchedTags.isEmpty) super.performExecute(argsMap, configMap, executionFlags)
      else {
        logger.info(s"The execution of plugin ${this.getClass.getSimpleName} has been skipped.")
        Map()
      }
    }
    else {
      super.performExecute(argsMap, configMap, executionFlags)
    }
  }
}
