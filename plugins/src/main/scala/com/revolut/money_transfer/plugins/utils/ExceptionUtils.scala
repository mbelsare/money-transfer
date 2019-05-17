package com.revolut.money_transfer.plugins.utils

import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import scalaj.http.HttpStatusException

object ExceptionUtils {

  implicit val formats = DefaultFormats

  def throwException(argsMap: collection.Map[String, Any], code: Int,
                     message: String, source: String): Unit = {
    throw HttpStatusException(code, source + ": " + message, write(argsMap))
  }
}
