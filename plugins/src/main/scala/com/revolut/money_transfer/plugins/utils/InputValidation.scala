package com.revolut.money_transfer.plugins.utils

import com.revolut.money_transfer.commons.StatusCodes
import com.revolut.money_transfer.plugins.utils.ExceptionUtils.throwException

object InputValidation {

  /**
    * Validates that required fields exist in a map.
    *
    * @param requiredKeys  The fields that are required in the instanceConfigMap.
    * @param mapToValidate The optional map to be validated
    * @param argsMap       Used to pass API request arguments to a plugin.
    *                      Stores an error code in case of an exception.
    * @param source        The name of the Class calling this method used to create an error message.
    */
  def validateMap(requiredKeys: List[String],
                  mapToValidate: collection.Map[String, Any],
                  argsMap: collection.Map[String, Any],
                  source: String): Unit = {
    val missing = requiredKeys.filter(k => !mapToValidate.isDefinedAt(k))
    if (missing.nonEmpty)
      throwException(argsMap, StatusCodes.BadRequest, s"${missing.mkString(", ")} not provided", source)
  }

}
