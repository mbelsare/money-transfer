package com.revolut.money_transfer.plugins.utils

import java.io.FileInputStream

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write

object PluginUtils {

  implicit val formats = DefaultFormats

  // $COVERAGE-OFF$
  def getStringAccounts(filePath: String): String = {
    write(parse(new FileInputStream(filePath)))
  }

}
