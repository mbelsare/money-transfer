package com.revolut.money_transfer.core.utils

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model._
import com.revolut.money_transfer.core.case_classes.RouteConfig
import com.revolut.money_transfer.core.core.cache.MessageCache
import com.revolut.money_transfer.core.core.services.TransactionMonitor
import com.typesafe.scalalogging.LazyLogging
import org.json4s.jackson.JsonMethods.{compact, parse, render}
import org.json4s.jackson.Serialization.write
import org.json4s.{DefaultFormats, _}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._
import scala.util.Try

class APIInterfaceHandler extends LazyLogging{

  implicit val formats = DefaultFormats

  /**
    * Retrieves the required return fields of a given DAG
    * @param dagName
    * @return
    */
  private def getRequiredFields(dagName: String, routeConfig: RouteConfig): Seq[String] =
    routeConfig.dags.filter(_.name == dagName).head.requiredFields :+ "errors"

  /**
    * Clears caches, retrieves required response fields, and replies with result to sender
    * @param transactionId
    * @param dagName
    * @param statusCode
    * @param requestDuration
    * @param response
    * @return
    */
  @throws(classOf[Exception])
  def sendResponse(transactionId: UUID,
                   dagName: String,
                   statusCode: Int,
                   requestDuration: Long,
                   response: collection.Map[String, Any], routeConfig: RouteConfig): HttpResponse = {
    APILogger.setRuntimeTags(MessageCache(transactionId))
    TransactionMonitor.clearCaches(transactionId, dagName)
    var requiredFields = getRequiredFields(dagName, routeConfig)
    val mutableMap = MutableMap() ++ response
    if (routeConfig.dags.filter(_.name == dagName).head.renameFields.isDefined)
      requiredFields = renameResponseFields(routeConfig, dagName, mutableMap, requiredFields)
    val responseBody = write(mutableMap.filter(e => requiredFields.contains(e._1)))
    val startTime = Instant.ofEpochMilli(System.currentTimeMillis() - requestDuration)
    val endTime = Instant.ofEpochMilli( System.currentTimeMillis())
    var logMessage = s"$transactionId -- $statusCode status -- $requestDuration ms -- $responseBody"
    if (APILogger.constantTags.toConfig.hasPath("logResponseFields")){
      logMessage = s"$transactionId -- $statusCode status -- $requestDuration ms" +
        s" -- startTime $startTime -- endTime $endTime"
      val logResponseFields = APILogger.constantTags.toConfig.getStringList("logResponseFields")
      val jsonBody = parse(responseBody)
      for (field <- logResponseFields.asScala) {
        if (responseBody.contains(field)) {
          val fieldVal = compact(render(jsonBody \\ field)).stripMargin
          logMessage += s" -- $field $fieldVal"
        }
      }
    }
    logger.info(logMessage)
    HttpResponse(StatusCode.int2StatusCode(statusCode),
      entity = HttpEntity(ContentType(MediaTypes.`application/json`), responseBody))
  }

  def convertToMap(o: Any): Map[String, Any] = {
    o match {
      case e: Map[String, Any] => e
      case _ => {
        val r = currentMirror.reflect(o)
        r.symbol.typeSignature.members.toStream
        .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s) }
        .map(r => r.symbol.name.toString.trim -> r.get.toString)
        .toMap
      }
    }
  }

  /**
    * This method renames the keys from the rename fields Map to its values
    * and filters out the keys that did not exist in the argsMap Map.
    * For example, required_fields = ["accountId", "errors"] and
    * rename_fields = Map("accountId" -> "accountNumber",
    * "errors.dagName" -> "workflowName","errors" -> "workflowErrors"),
    * the method will rename the keys, remove (key,value) pair from from
    * rename_fields map that did not exist in argsMap (example "errors.dagName")
    * if error  and return filtered keys from requiredFields
    * @param routeConfig
    * @param dagName
    * @param mutableMap
    * @param requiredFields
    * @return
    */
  def renameResponseFields(routeConfig: RouteConfig, dagName: String,
                           mutableMap: MutableMap[String, Any],
                           requiredFields: Seq[String]): Seq[String] = {
    val dag = routeConfig.dags.filter(_.name == dagName).head
    var renameFieldsMap = dag.renameFields.get
    val missing = renameFieldsMap.keySet.filter(k => !mutableMap.isDefinedAt(k))
    renameFieldsMap.foreach(rename_field => {
      if (rename_field._1.contains(".") && missing.contains(rename_field._1)) { //rename nested fields only
        val renameKeyRE = "(.+)\\.(.+)".r
        //for example, to rename errors.statusCode, keyTuple pair = (errors,statusCode) split by [.] Regex
        val keyTuple: (String, String) = rename_field._1 match {
          case (prefix)renameKeyRE(suffix) => (prefix, suffix)
        }

        if (mutableMap.isDefinedAt(keyTuple._1) && !keyTuple._1.contains(".") && keyTuple._1 != "body") {
          val objectListMap = mutableMap(keyTuple._1).asInstanceOf[List[Any]].map(e => {
            //convert Object in response (errors/modelResults) to a Map using Reflection
            convertToMap(e)
          }).map(m => { //rename & remove existing key from the Object Map
            val objectMap = MutableMap[String, Any]() ++ m
            objectMap(rename_field._2) = Try(
              objectMap(keyTuple._2) match {
                case e: UUID => objectMap(keyTuple._2).asInstanceOf[UUID].toString
                case _ => objectMap(keyTuple._2)}).get
            objectMap - keyTuple._2
          })
          mutableMap(keyTuple._1) = objectListMap
        }
        else if (keyTuple._1.contains(".")){
          throw new Exception("Cannot support renaming of response fields beyond 2 levels deep")
        }
      }
      else if ( missing.contains(rename_field._1) ) renameFieldsMap = renameFieldsMap - rename_field._1
      else mutableMap(rename_field._2) = mutableMap(rename_field._1) match {
        case e:UUID => mutableMap(rename_field._1).asInstanceOf[UUID].toString
        case _ => mutableMap(rename_field._1)
      }
    })
    requiredFields.filter(r => !renameFieldsMap.keySet.contains(r))
  }
}
