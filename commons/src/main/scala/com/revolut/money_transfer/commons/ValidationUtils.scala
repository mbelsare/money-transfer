package com.revolut.money_transfer.commons

object ValidationUtils {

  /**
    * Returns a boolean indicating whether all the keys exist in a map
    *
    * @param mapToValidate The map to be validated
    * @param keys          The keys to check for existence in mapToValidate.
    * @return Boolean indicating whether all the keys exist in a map
    */
  def mapContainsKeys(mapToValidate: collection.Map[String, Any],
                      keys: List[String]): Boolean = {
    val missing = keys.filter(k => !mapToValidate.contains(k))
    missing.isEmpty
  }

}
