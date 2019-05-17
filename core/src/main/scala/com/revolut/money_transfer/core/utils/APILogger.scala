package com.revolut.money_transfer.core.utils

import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

import scala.collection.JavaConverters._

object APILogger extends LazyLogging {
  val CONFIG_KEY: String = "loggingMetaTags"
  val DEFAULT_VALUE: String = ""
  val DEFAULT_CONFIG = ConfigFactory.parseString(
    s"""
      |$CONFIG_KEY {
      |  appLogLevel = "info"
      |  constant {}
      |  runtime = []
      |}
    """.stripMargin)


  var constantTags: ConfigObject = _
  var runtimeTags: List[String] = List()
  var logLevelTag: String = _

  def info(input: String): Unit = {
    logger.info(input)
  }

  def error(input: String): Unit = {
    logger.error(input)
  }

  def debug(input: String): Unit = {
    logger.debug(input)
  }

  def configure(config: Config): Unit = {
    constantTags = config.withFallback(DEFAULT_CONFIG).getObject(s"$CONFIG_KEY.constant")
    runtimeTags = config.withFallback(DEFAULT_CONFIG).getStringList(s"$CONFIG_KEY.runtime").asScala.toList
    logLevelTag = config.withFallback(DEFAULT_CONFIG).getString(s"$CONFIG_KEY.appLogLevel")


    if (constantTags.isEmpty || runtimeTags.isEmpty || logLevelTag.isEmpty) {
      logger.warn("LoggingMetaTags are not properly configured.")
    }
  }

  def setConstantTags(): Unit = {
    constantTags.keySet().forEach(tag => {
      //MDC is logback's equivalent to Log4j2's Thread Context Map
      // https://logging.apache.org/log4j/2.x/manual/thread-context.html
      MDC.put(tag, constantTags.get(tag).render())
    })
  }

  @inline
  def setRuntimeTags(argsMap: collection.Map[String, Any]): Unit = {
    runtimeTags.foreach(tag => {
      if (argsMap.contains(tag)) {
        MDC.put(tag, argsMap(tag).asInstanceOf[String])
      } else {
        MDC.put(tag, DEFAULT_VALUE)
      }
    })
  }
}
