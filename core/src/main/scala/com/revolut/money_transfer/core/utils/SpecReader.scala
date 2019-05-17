package com.revolut.money_transfer.core.utils

import com.revolut.money_transfer.core.case_classes._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

/**
  * Spec Reader is a generic class to read/load a JSON specs/config
  */
object SpecReader {
  implicit val format = DefaultFormats

  def load[T](jsonFile: String)(implicit m: Manifest[T]): T = {
    val jsonText = scala.io.Source.fromFile(jsonFile).mkString
    parse(jsonText).extract[T]
  }

  def loadRouteConfig(jsonFile: String): RouteConfig = {
    load[RouteConfig](jsonFile)
  }
}
