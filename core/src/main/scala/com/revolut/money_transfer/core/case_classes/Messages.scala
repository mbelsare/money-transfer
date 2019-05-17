package com.revolut.money_transfer.core.case_classes

import java.net.URLClassLoader
import java.util.UUID

import akka.actor.{ActorRef, ActorRefFactory}
import com.revolut.money_transfer.commons.StatusCodes

sealed trait ErrorMessage {
  val message: String
  val statusCode: Int
}

sealed trait WorkflowResponse {
  val statusCode: Int
  val transactionId: UUID
  val dagName: String
}

final case class WorkflowResult(transactionId: UUID,
                                dagName: String) extends WorkflowResponse {
  override val statusCode = StatusCodes.OK
}

final case class WorkflowError(message: String,
                               statusCode: Int,
                               transactionId: UUID,
                               dagName: String) extends WorkflowResponse with ErrorMessage

sealed trait PluginResponse {
  val statusCode: Int
  val result: collection.Map[String, Any]
}

final case class PluginResult(result: collection.Map[String, Any]) extends PluginResponse {
  override val statusCode = StatusCodes.OK
}

final case class PluginError(message: String,
                             trace: String,
                             statusCode: Int,
                             result: collection.Map[String, Any]) extends PluginResponse with ErrorMessage

sealed trait ExecutorResponse {
  val actorName: String
  val statusCode: Int
  val transactionId: UUID
  val dagName: String
}

final case class ExecutorResult(actorName: String, transactionId: UUID, dagName: String)
  extends ExecutorResponse {
  override val statusCode = StatusCodes.OK
}

final case class ExecutorError(message: String,
                               statusCode: Int,
                               actorName: String,
                               transactionId: UUID,
                               dagName: String) extends ExecutorResponse with ErrorMessage

sealed trait StartupResponse

final case class StartupResult() extends StartupResponse

final case class StartupError(message: String, statusCode: Int) extends StartupResponse with ErrorMessage

sealed trait ReflectMessage {
  val classLoader: java.net.URLClassLoader
  val classPath: String
  val request: PluginRequest
  val retries: Int
}

final case class Reflect(classLoader: java.net.URLClassLoader,
                         classPath: String,
                         request: PluginRequest,
                         retries: Int) extends ReflectMessage

final case class ReflectRetry(classLoader: java.net.URLClassLoader,
                              classPath: String,
                              request: PluginRequest,
                              retries: Int,
                              sender: ActorRef) extends ReflectMessage

sealed trait PluginRequest {
  val config: collection.Map[String, Any]
}

final case class PluginExecute(argsMap: collection.Map[String, Any],
                               config: collection.Map[String, Any],
                               executionFlags: collection.Map[String, Any]) extends PluginRequest

final case class PluginStartup(config: collection.Map[String, Any]) extends PluginRequest

final case class PluginShutdown(config: collection.Map[String, Any]) extends PluginRequest

final case class ExecuteWorkflow(dagName: String, transactionId: UUID)

final case class TaskMonitorMessage(transactionId: UUID, actorName: String, dagName: String)

final case class ExecutePlugin(transactionId: UUID, dagName: String)

sealed trait ExecutorStartup {
  val actorName: String
}
final case class CreateExecutors(context: ActorRefFactory,
                                 actors: List[ActorSpec], pluginLibClassLoader: URLClassLoader,
                                 executorStartupDelay: Int, executorDispatcherType: String)

final case class ExecutorReady(actorName: String) extends ExecutorStartup

final case class ExecutorStartupFailed(message: String, statusCode: Int, actorName: String)
  extends ExecutorStartup with ErrorMessage

final case class Startup()

final case class BuildRoute(routes: List[RouteDetails]) {}
