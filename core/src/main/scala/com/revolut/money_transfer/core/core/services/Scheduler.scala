package com.revolut.money_transfer.core.core.services

import java.net.URLClassLoader
import java.util

import akka.ConfigurationException
import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.ActorInterface
import com.revolut.money_transfer.core.core.cache.PluginExecutionMirrorCache
import com.revolut.money_transfer.core.core.factory.DagFactory
import com.revolut.money_transfer.core.utils.ExecutorConfigCombiner
import com.revolut.money_transfer.commons.StatusCodes
import com.revolut.money_transfer.core.case_classes.{ActorSpec, RouteConfig, ScalaGraphJSON}
import com.revolut.money_transfer.core.core.executor.ExecutorStartupHandler
import com.revolut.money_transfer.core.rest.RouteGenerator
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class Scheduler(val actorSystem: ActorSystem,
                val actorSystemTimeout: Timeout,
                val serviceConfig: Map[String, AnyRef],
                val routeConfig: RouteConfig,
                pluginLibClassLoader: URLClassLoader) extends ActorInterface
  with Actor
  with LazyLogging {

  val dags: List[ScalaGraphJSON] = routeConfig.dags
  val actors: List[ActorSpec] = routeConfig.actors
  var listActors: List[String] = List[String]()
  routeConfig.actors.foreach(x => {listActors = x.name :: listActors})
  override implicit val system = this.actorSystem
  override implicit val timeout = this.actorSystemTimeout
  override implicit val ec: ExecutionContext = system.dispatcher
  private var startupSender: ActorRef = _
  private val initializedExecutors = new ListBuffer[String]()
  private var initializedExecutorsCount = mutable.Map[String, Int]()
  private val appRunners: util.HashMap[String, String] = if (serviceConfig.contains("appRunners")) {
    serviceConfig("appRunners").asInstanceOf[java.util.HashMap[String, String]]
  } else {
    new util.HashMap[String, String]()
  }
  private val executorStartupDelay = serviceConfig.getOrElse("executorStartupDelay", 1500).asInstanceOf[Int]
  private val executorDispatcherType = serviceConfig.getOrElse("executorDispatcherType",
    "akka.actor.default-dispatcher").asInstanceOf[String]

  private val numActorInstances = serviceConfig.getOrElse("numActorInstances", 10).asInstanceOf[Int]
  val totalNumActorInstancesMap: mutable.Map[String, Int] = ExecutorConfigCombiner.getCombinedActorInstances(actors)
  var finishedExecutors: Set[String] = Set[String]()

  var startupComplete = false

  override def receive: Receive = {
    case _: Startup => {
      startupSender = sender()

      if (!DagFactory.initializeDags(dags))
        startupSender ! StartupError("Dag initialization failed", StatusCodes.InternalServerError)

      val routeActor = Orchestrator.createActor(context, numActorInstances)

      if(appRunners.getOrDefault("rest", "true").toBoolean) {
        RouteGenerator.createActor(context,
          system,
          timeout,
          routeConfig,
          routeActor,
          numActorInstances)
      }

      TaskMonitor.createActor(context, numActorInstances)

      ExecutorStartupHandler.createActor(context)

      context.actorSelection(ExecutorStartupHandler.actorPath) !
        CreateExecutors(context, actors, pluginLibClassLoader, executorStartupDelay, executorDispatcherType)

      if (actors.isEmpty) startupSender ! StartupResult()
    }

    case msg: ExecutorReady =>
      initializedExecutors += msg.actorName
      if (!initializedExecutorsCount.contains(msg.actorName)) {
        initializedExecutorsCount += (msg.actorName -> 1)
      } else {
        val newCount = initializedExecutorsCount(msg.actorName) + 1
        initializedExecutorsCount += (msg.actorName -> newCount)
      }

      if (initializedExecutorsCount(msg.actorName) >= 0.8 * totalNumActorInstancesMap(msg.actorName)
        && !finishedExecutors.contains(msg.actorName)) {
        logger.info(s"${msg.actorName} passed threshold of created actors")
        finishedExecutors += msg.actorName
      }

      logger.debug(s"${msg.actorName}" +
        s" - ${initializedExecutorsCount(msg.actorName)} / ${totalNumActorInstancesMap(msg.actorName)}")

      logger.debug(s"${msg.actorName} ready")
      logger.debug(s"Initialized executors so far: $initializedExecutorsCount")
      logger.debug(s"Initialized executors length: ${initializedExecutors.length} -- total executors: " +
        s"${totalNumActorInstancesMap.values.sum}")
      logger.debug(s"Remaining executors to initialize: ${listActors diff initializedExecutors}")
      logger.debug(s"Initialized executors past threshold: $finishedExecutors -- total initialized executors: " +
        s"${finishedExecutors.size}")

      if (finishedExecutors.size == totalNumActorInstancesMap.keys.size && !startupComplete) {
        logger.info(s"All executors passed threshold, starting Revolut API App")
        startupComplete = true
        startupSender ! StartupResult()
      }

    case msg: ExecutorStartupFailed =>
      startupSender ! StartupError(s"Plugin initialization failed: ${msg.message}", msg.statusCode)
  }

}

object Scheduler extends LazyLogging {

  private implicit val timeout = Timeout(4.hours)
  private var _name = "Scheduler"

  def name: String = _name

  def actorPath: String = s"/user/$name"

  // Note: This should be created once at the top level
  def startup(actorSystem: ActorSystem,
              actorSystemTimeout: Timeout,
              serviceConfig: Map[String, AnyRef],
              routeConfig: RouteConfig,
              pluginLibClassLoader: URLClassLoader,
              actorName: String = "Scheduler"): StartupResponse = {
    val context: ActorRefFactory = actorSystem

    _name = actorName

    val scheduler = Props(new Scheduler(actorSystem,
      actorSystemTimeout,
      serviceConfig,
      routeConfig,
      pluginLibClassLoader))

    try {
      context.actorOf(scheduler.withDispatcher("akka.actor.core-dispatcher"), name)
      logger.info(s"Scheduler set dispatcher as core-dispatcher")
    } catch {
      case _: ConfigurationException => {
        context.actorOf(scheduler, name)
        logger.info(s"akka.actor.core-dispatcher not found. Scheduler set dispatcher as default-dispatcher")
      }
    }

    Await.result(
      context.actorSelection(Scheduler.actorPath) ? Startup(), timeout.duration).asInstanceOf[StartupResponse]
  }

  def shutdown(actorSystem: ActorSystem): Unit = {
    actorSystem.terminate()
    Await.result(actorSystem.whenTerminated, Duration.Inf)
    PluginExecutionMirrorCache.clear()
  }

}
