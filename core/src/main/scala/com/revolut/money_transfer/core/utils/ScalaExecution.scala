package com.revolut.money_transfer.core.utils

import java.lang.reflect.InvocationTargetException

import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.cache.PluginExecutionMirrorCache
import com.revolut.money_transfer.commons.StatusCodes
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.ListBuffer
import scala.reflect.runtime.universe
import scalaj.http.HttpStatusException

class ScalaExecution extends LazyLogging {

  def scalaInvocation(classLoader: java.net.URLClassLoader,
                      classPath: String,
                      instanceIdentifier: String,
                      request: PluginRequest): PluginResponse = {

    val startup: String = "startup"
    val execute: String = "performExecute"
    val shutdown: String = "shutdown"
    implicit val formats = DefaultFormats

    try {
      request match {
        case req: PluginStartup =>
          /*
            NOTE: Create All the Reflection Mirrors in the beginning,
            this will reduce the overhead during execution phase
          */
          val mirror = universe.runtimeMirror(classLoader)
          val module = mirror.staticModule(classPath)
          val moduleMirror = mirror.reflectModule(module)
          val instanceMirror = mirror.reflect(moduleMirror.instance)
          val methodStartup = moduleMirror.symbol.typeSignature.member(universe.TermName(startup)).asMethod
          val methodExecute = moduleMirror.symbol.typeSignature.member(universe.TermName(execute)).asMethod
          val methodShutdown = moduleMirror.symbol.typeSignature.member(universe.TermName(shutdown)).asMethod
          val mirrorMap: Map[String, Any] = Map[String, Any](
            "mirror" -> mirror,
            "module" -> module,
            "moduleMirror" -> moduleMirror,
            "instanceMirror" -> instanceMirror,
            "methodStartup" -> methodStartup,
            "methodExecute" -> methodExecute,
            "methodShutdown" -> methodShutdown
          )
          // Add the mirror information to the cache
          PluginExecutionMirrorCache.add(classPath + instanceIdentifier, mirrorMap)
          PluginResult(instanceMirror.reflectMethod(methodStartup)(req.config)
            .asInstanceOf[collection.Map[String, Any]])
        case req: PluginExecute =>
          val method = PluginExecutionMirrorCache.getExecuteMethod(classPath + instanceIdentifier).get
          PluginResult(PluginExecutionMirrorCache.getInstanceMirror(classPath + instanceIdentifier).get
            .reflectMethod(method)(req.argsMap, req.config, req.executionFlags)
            .asInstanceOf[collection.Map[String, Any]])
        case req: PluginShutdown =>
          logger.debug(s"Received shutdown request for $classPath")
          val method = PluginExecutionMirrorCache.getShutdownMethod(classPath + instanceIdentifier).get
          val instanceList = ListBuffer[String]()
          /*
            The following allows an actor shutdown invocation to also shutdown the remaining actor instances properly
           */
          PluginExecutionMirrorCache.getKeys().foreach(x => {
            if(x.contains(classPath) && x != s"${classPath + instanceIdentifier}") instanceList += x
          })
          instanceList.foreach(x => PluginExecutionMirrorCache.getInstanceMirror(x).get
            .reflectMethod(PluginExecutionMirrorCache.getShutdownMethod(x).get)(req.config)
            .asInstanceOf[collection.Map[String, Any]])
          PluginResult(PluginExecutionMirrorCache.getInstanceMirror(classPath + instanceIdentifier).get
            .reflectMethod(method)(req.config)
            .asInstanceOf[collection.Map[String, Any]])
      }
    } catch {
      case e: InvocationTargetException => e.getTargetException match {
        case e: HttpStatusException => {
          PluginError(e.toString, ExceptionUtils.getStackTrace(e), e.code,
            parse(e.body).extract[collection.Map[String, Any]])
        }
        case e: Throwable => {
          PluginError(e.toString, ExceptionUtils.getStackTrace(e), StatusCodes.InternalServerError.intValue, Map())
        }
      }
      case e: Throwable => {
        PluginError(e.toString, ExceptionUtils.getStackTrace(e), StatusCodes.InternalServerError.intValue, Map())
      }
    }
  }
}
