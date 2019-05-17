package com.revolut.money_transfer.core.app

import java.io.File
import java.lang.management.ManagementFactory
import java.util
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ch.qos.logback.classic.{Level, Logger}
import com.codahale.metrics.jvm._
import com.codahale.metrics.{MetricRegistry, Slf4jReporter}
import com.revolut.money_transfer.core.case_classes.{RouteConfig, StartupError, StartupResult, _}
import com.revolut.money_transfer.core.core.ActorInterface
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.core.services.Scheduler
import com.revolut.money_transfer.core.rest.{RestServer, RouteGenerator}
import com.revolut.money_transfer.core.utils._
import com.typesafe.scalalogging.LazyLogging
import nl.grons.metrics4.scala.DefaultInstrumented
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}

/**
  * Parses and loads Route and Application configurations into a Revolut object.
  * If route file, config file or plugin lib are not defined or not found, log an exception and exit.
  * RevolutApp has a shutdown hook, to stop and terminate itself.
  */

// $COVERAGE-OFF$
object RevolutApp extends App with ActorInterface with LazyLogging with DefaultInstrumented {

  logger.info("starting initialization")
  if (Option(System.getProperty("route.file")).getOrElse("").isEmpty) {
    logger.error("route.file not defined")
    System.exit(1)
  }
  if (Option(System.getProperty("prop.file")).getOrElse("").isEmpty) {
    logger.error("prop.file not defined")
    System.exit(1)
  }
  if (Option(System.getProperty("plugin.lib")).getOrElse("").isEmpty) {
    logger.error("plugin.lib not defined")
    System.exit(1)
  }

  val routeFile = System.getProperty("route.file")
  if (!new File(routeFile).isFile) {
    logger.error("route.file: \"" + routeFile + "\" not found")
    System.exit(1)
  }

  val propFile = new File(System.getProperty("prop.file"))
  if (!propFile.isFile) {
    logger.error("prop.file: \"" + propFile + "\" not found")
    System.exit(1)
  }

  val routeConfig: RouteConfig = SpecReader.loadRouteConfig(routeFile)
  validateConfig(routeConfig)

  val propConfig = ConfigFactory.populateConfig(propFile, routeConfig.actors)
  logger.debug("RevolutConfig values are: " + RevolutAppConfig.config.get)

  val pluginLibClassFile: String = System.getProperty("plugin.lib")
  val pluginLibClassLoader = PluginLibJar.load(pluginLibClassFile)
  val timeoutInSeconds = RevolutAppConfig.config.get.getOrElse("responseTimeoutSeconds", 15).asInstanceOf[Int]

  val logLevel = Level.toLevel(RevolutAppConfig.config.get.getOrElse("logLevelOverride", "INFO").asInstanceOf[String])
  setLogLevel(logLevel)

  APILogger.configure(propConfig)
  APILogger.setConstantTags()

  override implicit val system = ActorSystem(routeConfig.actor_system_name, propConfig)
  override implicit val timeout: Timeout = Timeout(timeoutInSeconds, TimeUnit.SECONDS)
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = {
    if (system.dispatchers.hasDispatcher("akka.actor.core-dispatcher")) {
      APILogger.info("Found core dispatcher, setting as dispatcher")
      system.dispatchers.lookup("akka.actor.core-dispatcher")
    } else {
      APILogger.info("Could not find akka.actor.core-dispatcher, setting default dispatcher")
      system.dispatchers.defaultGlobalDispatcher
    }
  }

  logger.info(s"JVM Metrics toggle is ${RevolutAppConfig.config.get.getOrElse("metrics", false)}")
  val metricsToggle: Boolean = RevolutAppConfig.config.get.getOrElse("metrics", false) match {
    case metrics: Boolean => metrics
    case metrics: String => if (metrics.toLowerCase() == "true") true else false
    case _ => false
  }

  val reporterTimeInSec = 300
  val reporter: Slf4jReporter = Slf4jReporter.forRegistry(MetricsRegistryFactory.metricRegistry)
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .build()

  if (metricsToggle) {
    MetricsRegistryFactory.metricRegistry.register("jvm-gc", new GarbageCollectorMetricSet)
    MetricsRegistryFactory.metricRegistry.register("jvm-buffer",
      new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer))
    MetricsRegistryFactory.metricRegistry.register("jvm-memory", new MemoryUsageGaugeSet)
    MetricsRegistryFactory.metricRegistry.register("jvm-threads", new ThreadStatesGaugeSet)

    reporter.start(reporterTimeInSec, TimeUnit.SECONDS)
  }

  val targetEnv = propConfig.getString("targetEnv").toLowerCase
  val configMap = propConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  val appRunners: util.HashMap[String, String] = if (serviceConfig.contains("appRunners")) {
    serviceConfig("appRunners").asInstanceOf[java.util.HashMap[String, String]]
  } else {
    new util.HashMap[String, String]()
  }

  val restRunner: Boolean = appRunners.getOrDefault("rest", "true").toBoolean

  Scheduler.startup(system,
    timeout,
    serviceConfig.toMap,
    routeConfig,
    pluginLibClassLoader) match {
    case msg: StartupError =>
      logger.error(msg.message)
      system.terminate()
      System.exit(1)
    case _: StartupResult =>
  }

  var route: Route = _
  val server = new RestServer()

  if (restRunner) {
    logger.info("Starting REST server")
    route = Await.result(system.actorSelection(RouteGenerator.actorPath) ? BuildRoute(routeConfig.routes),
      timeout.duration).asInstanceOf[Route]

    val host = routeConfig.host
    val port = routeConfig.port
    val uri = s"http://$host:$port"

    server.start(host, port, route)

    logger.info(s"Config File: $routeFile")
    logger.debug(s"App Conf: $propConfig") // This is log level debug since it may contain secrets
    logger.info(s"Plugin Lib: $pluginLibClassFile")
    logger.info(s"Request $uri for POST endpoint")
    logger.info(s"Waiting for requests at $uri...\n")
  }

  sys.addShutdownHook(stopRevolutApp)

  private def stopRevolutApp = {
    logger.debug("Received a shutdown hook, shutting down the application")
    if (metricsToggle) reporter.stop()

    if (restRunner) server.stop()

    Scheduler.shutdown(system)
  }

  private def validateConfig(config: RouteConfig) {
    if (config.actors.map(_.name).lengthCompare(config.actors.length) != 0) {
      logger.error("Actor names not unique")
      System.exit(1)
    } else if (config.dags.map(_.name).lengthCompare(config.dags.length) != 0) {
      logger.error("Dag names not unique")
      System.exit(1)
    } else if (config.dags.exists(d => config.actors.exists(a => d.name.endsWith(a.name)))) {
      logger.error("Dag names must not end with an actor's name")
      System.exit(1)
    } else if (config.dags.exists(d => config.actors.exists(a => a.name.startsWith(d.name)))) {
      logger.error("Actor names must not start with a dag's name")
      System.exit(1)
    } else if (config.routes.exists(r => r.path_prefixes.isEmpty)) {
      logger.error("path_prefixes must not be empty")
      System.exit(1)
    } else if (config.routes.exists(r => r.health_check_path_prefixes.isEmpty)) {
      logger.error("health_check_path_prefixes must not be empty")
      System.exit(1)
    } else if (config.dags.exists(d => d.nodes._2.isEmpty)) {
      logger.error("Dag nodes must not be empty")
      System.exit(1)
    } else if (config.routes.isEmpty) {
      logger.error("routes must not be empty")
      System.exit(1)
    } else if (config.dags.isEmpty) {
      logger.error("dags must not be empty")
      System.exit(1)
    } else if (config.routes.exists(r => !config.dags.exists(d => d.name.contains(r.default_dag)))) {
      logger.error("default_dag must refer to a valid dag initial name")
      System.exit(1)
    }
  }

  private def setLogLevel(level: Level): Unit = {
    LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger].setLevel(level)
  }
}

object MetricsRegistryFactory {
  /** The application wide metrics registry. */
  val metricRegistry = new MetricRegistry()
}

