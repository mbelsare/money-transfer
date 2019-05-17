package com.revolut.money_transfer.plugins.utils

import com.revolut.money_transfer.commons.StatusCodes
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write
import org.scalatest.{FlatSpec, Matchers}
import scalaj.http.HttpStatusException

import scala.collection.mutable.{Map => MutableMap}

class ExceptionUtilsTest extends FlatSpec with Matchers {

  implicit val formats = DefaultFormats

  behavior of "Throw Exception"
  it should "throw an HttpRequestException with pertinent info" in {
    val argsMap: MutableMap[String, Any] = MutableMap[String, Any]("test" -> "123")
    val errorMessage: String = "Oh no, an error occurred"
    try {
      ExceptionUtils.throwException(argsMap, StatusCodes.NotFound,
        errorMessage, this.getClass.getSimpleName)
    } catch {
      case e: HttpStatusException =>
        assert(e.code == StatusCodes.NotFound)
        assert(e.statusLine == getClass.getSimpleName + ": " + errorMessage)
        assert(e.body == write(argsMap))
    }
  }

}

