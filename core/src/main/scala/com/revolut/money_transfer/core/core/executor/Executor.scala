package com.revolut.money_transfer.core.core.executor

import java.net.{URLClassLoader => ClassLoader}

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, SupervisorStrategy}
import akka.pattern.{CircuitBreaker, ask}
import akka.routing._
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.utils.{ExecutorConfigCombiner, RevolutAppConfig, ScalaExecution}
import com.revolut.money_transfer.commons.StatusCodes
import com.revolut.money_transfer.core.case_classes.ActorSpec
import com.revolut.money_transfer.core.core.services.{Orchestrator, Scheduler}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Executor executes a specific plugin
  * Orchestrator calls executor and executor responds to Orchestrator
  * @param classLoader jar containing plugin class
  * @param classPath   class path for plugin
  * @param actorName   unique name for actor
  */
class Executor(classLoader: ClassLoader,
               classPath: String,
               actorName: String,
               executionFlagsMap: Map[String, collection.Map[String, Any]] =
               Map[String, collection.Map[String, Any]](),
               executorStartupDelay: Int,
               scalaExecution: ScalaExecution = new ScalaExecution()) extends Actor with LazyLogging {
  var reflector: ActorRef = _
  val defaultRetries = 0
  val retries: Int = RevolutAppConfig.config.get.getOrElse("retries", defaultRetries).asInstanceOf[Int]
  val defaultTimeout = 10
  implicit val timeout: Timeout = Timeout(
    (RevolutAppConfig.config.get.getOrElse("pluginTimeoutInSeconds", defaultTimeout).asInstanceOf[Int] + 1).seconds)

  import context.dispatcher

  val breaker: CircuitBreaker = new CircuitBreaker(
    context.system.scheduler,
    maxFailures = RevolutAppConfig.config.get.getOrElse("circuitBreakerMaxExecutorFailures", 10).asInstanceOf[Int],
    callTimeout = timeout.duration - 1.second,
    resetTimeout = RevolutAppConfig.config.get.getOrElse("circuitBreakerExecutorResetTimeout", 10)
      .asInstanceOf[Int].seconds)
    .onOpen(logger.info(s"$actorName ${self.toString()} CircuitBreaker open"))
    .onClose(logger.info(s"$actorName ${self.toString()} CircuitBreaker closed"))
    .onHalfOpen(logger.info(s"$actorName ${self.toString()} CircuitBreaker half-open"))

  /**
    * Called right after the actor is started.
    * During restarts it is called by the default implementation of postRestart.
    * Performs plugin startup and adds returned plugin configuration to ConfigMap
    */
  override def preStart() {
    logger.debug(StringContext("Calling ", " startup()").s(classPath))
    val startTime = System.currentTimeMillis()
    val future = ask(self, Reflect(
      classLoader,
      classPath,
      PluginStartup(RevolutAppConfig.config.get ++ ConfigCache.get(actorName).getOrElse(Map())),
      retries))(30.minutes)
    future.onComplete {
      case Success(msg) => msg match {
        case msg: PluginResult =>
          ConfigCache.add(actorName + this.toString, ConfigCache.get(actorName).get)
          ConfigCache.append(actorName + this.toString, msg.result)
          val completionTime = System.currentTimeMillis() - startTime
          logger.debug(s"${actorName} completed startup in ${completionTime} ms")
          context.actorSelection(Scheduler.actorPath) ! ExecutorReady(actorName)
        case msg: PluginError =>
          logger.error(s"${actorName} threw the following error on prestart: ${msg.trace}")
          context.actorSelection(Scheduler.actorPath) ! ExecutorStartupFailed(msg.message, msg.statusCode, actorName)
      }
      case Failure(throwable) =>
        context.actorSelection(Scheduler.actorPath) ! ExecutorStartupFailed(
          throwable.toString,
          StatusCodes.InternalServerError,
          actorName)
    }

    Thread.sleep(executorStartupDelay)
  }

  override def postStop() {
    logger.debug(s"Calling Shutdown next for ${classPath} - ${classLoader} - ${this.toString}")
    logger.debug(StringContext("Calling ", " shutdown()").s(classPath))
    scalaExecution.scalaInvocation(
      classLoader,
      classPath,
      this.toString,
      PluginShutdown(ConfigCache.get(actorName + this.toString).getOrElse(Map())))
  }

  override def receive: Receive = {
    case msg: ExecutePlugin =>
      executePlugin(msg)
    case msg: ReflectMessage =>
      reflect(msg)
  }

  /**
    * Gathers config and messages from caches, extracts dag-specific execution flags, and does a self ask
    * to the reflect function. This allows for retries and the use of CircuitBreaker on a per-plugin basis
    * Appends plugin response to MessageCache
    *
    * @param msg message containing transaction details
    */
  private def executePlugin(msg: ExecutePlugin) {
    val startTime = System.currentTimeMillis()
    logger.debug(StringContext("transactionId, ", ": Calling ", " execute()").s(msg.transactionId, classPath))
    logger.debug(s"Dag ${msg.dagName} is executing actor ${actorName} with execution flags: " +
      s"${executionFlagsMap.getOrElse(msg.dagName, Map())}")


    val future = breaker.withCircuitBreaker({
      self ? Reflect(
        classLoader,
        classPath,
        PluginExecute(MessageCache(msg.transactionId), RevolutAppConfig.config.get ++
          ConfigCache.get(actorName + this.toString).
          getOrElse(Map()), executionFlagsMap.getOrElse(msg.dagName, Map())),
        retries)
    })

    future.onComplete {
      case Success(response) => response match {
        case response: PluginResult =>
          val completionTime = System.currentTimeMillis() - startTime
          logger.info(s"transactionId ${msg.transactionId}: ${actorName} completed in ${completionTime} ms")
          MessageCache.appendIfDefined(msg.transactionId, response.result)
          context.actorSelection(Orchestrator.actorPath) ! ExecutorResult(actorName, msg.transactionId, msg.dagName)
        case response: PluginError =>
          logger.error(s"transactionId ${msg.transactionId}: ${actorName} threw the following error on " +
            s"execution: ${response.trace}")
          MessageCache.appendIfDefined(msg.transactionId, response.result)
          context.actorSelection(Orchestrator.actorPath) ! ExecutorError(response.message,
            response.statusCode,
            actorName,
            msg.transactionId,
            msg.dagName)
      }
      case Failure(failure) =>
        context.actorSelection(Orchestrator.actorPath) ! ExecutorError(failure.toString,
          StatusCodes.InternalServerError,
          actorName,
          msg.transactionId,
          msg.dagName)
    }
  }

  /**
    * Makes a call to ScalaExecution to execute plugin
    * Retries on failure by doing a self tell
    *
    * @param msg
    */
  private def reflect(msg: ReflectMessage) {
    scalaExecution.scalaInvocation(msg.classLoader, msg.classPath, this.toString, msg.request) match {
      case result: PluginResult =>
        msg match {
          case _: Reflect => sender ! result
          case m: ReflectRetry => m.sender ! result
        }
      case error: PluginError =>
        if (msg.retries > 0) {
          logger.error(s"Error occured in ${msg.classPath}: PluginError(${error.message}," +
            s"${error.statusCode},${error.result}")
          logger.error(s"${actorName} threw the following error on retry ${msg.retries}: ${error.trace}")
          self ! ReflectRetry(msg.classLoader, msg.classPath, msg.request, msg.retries - 1,
            if (msg.isInstanceOf[Reflect]) sender else msg.asInstanceOf[ReflectRetry].sender)
        } else {
          msg match {
            case _: Reflect =>
              sender ! error
            case m: ReflectRetry =>
              m.sender ! error
          }
        }
    }
  }

}

object Executor {

  private val _name = "Executor"
  private implicit val timeout: Timeout = Timeout(10.second)

  def name(execName: String): String = s"$execName${_name}"

  def actorPath(execName: String): String =
    s"${Scheduler.actorPath}/$execName${_name}"

  def createActors(context: ActorRefFactory,
                   actors: List[ActorSpec],
                   pluginLibClassLoader: ClassLoader,
                   executorStartupDelay: Int,
                   dispatcherType: String) {

    val numActorInstancesMap = ExecutorConfigCombiner.getCombinedActorInstances(actors)
    val executionFlagsMap = ExecutorConfigCombiner.getCombinedExecutionFlags(actors)

    var actorSet = Set[String]()

    actors.foreach(a => {
      if (!actorSet.contains(a.name)) {
        context.actorOf(Props(new Executor(pluginLibClassLoader,
          a.class_path,
          a.name,
          executionFlagsMap(a.name),
          executorStartupDelay))
          .withRouter(RoundRobinPool(numActorInstancesMap(a.name),
            supervisorStrategy = SupervisorStrategy.defaultStrategy)),
          name(a.name))
        actorSet += a.name
      }
    })
  }
}
