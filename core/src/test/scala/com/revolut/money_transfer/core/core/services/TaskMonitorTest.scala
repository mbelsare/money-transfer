
package com.revolut.money_transfer.core.core.services

import java.io.File
import java.net.URLClassLoader
import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{RouteConfig, TaskMonitorMessage}
import com.revolut.money_transfer.core.core.TASK_STATE
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.{ConfigFactory, TaskFactory}
import com.revolut.money_transfer.core.utils.{PluginLibJar, RevolutAppConfig, APILogger, SpecReader}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class TaskMonitorTest extends FlatSpec with BeforeAndAfterAll {

  val configFile = "core/src/test/resources/conf/app_route_task_monitor.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val pluginLibClassFile: String = "core/external_libs/plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(pluginLibClassFile)
  val appConfig = ConfigFactory.populateConfig(new File("core/src/test/resources/conf/sample_app_test.conf"),
    routeConfig.actors)
  APILogger.configure(appConfig)
  APILogger.setConstantTags()
  val targetEnv = appConfig.getString("targetEnv").toLowerCase
  val configMap = appConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  private implicit val timeout = Timeout(10.seconds)
  implicit val system = ActorSystem(routeConfig.actor_system_name, appConfig)
  implicit val materializer = ActorMaterializer()
  val taskMonitor = system.actorOf(Props(new TaskMonitor()))
  Scheduler.startup(system, timeout, serviceConfig.toMap, routeConfig, classLoader)

  val tid = UUID.randomUUID()
  val taskName = "db"
  val predecessorOne = "flattenBody"
  val predecessorTwo = "parser"
  val dagName = "dagname"
  val testTimeout = 300
  TaskFactory.populateTasks(DagCache(dagName), tid)
  MessageCache.add(tid, Map())
  ErrorCache.init(tid)

  override def afterAll() {
    Scheduler.shutdown(system)
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  it should "not reply to the sender if tasks are incomplete" in {
    TaskCache.updateTaskState(tid, taskName, TASK_STATE.DORMANT)
    TaskCache.updateTaskState(tid, predecessorOne, TASK_STATE.RUNNING)
    TaskCache.updateTaskState(tid, predecessorTwo, TASK_STATE.FINISHED_SUCCESSFULLY)
    taskMonitor ! TaskMonitorMessage(tid, taskName, dagName)
    Thread.sleep(testTimeout)
    assert(TaskCache.getTaskState(tid, taskName) == TASK_STATE.DORMANT)
  }

  it should "send a message to the executor if task has no predecessors" in {
    TaskCache.updateTaskState(tid, predecessorOne, TASK_STATE.DORMANT)
    taskMonitor ! TaskMonitorMessage(tid, predecessorOne, dagName)
    Thread.sleep(testTimeout)
    assert(TaskCache.getTaskState(tid, predecessorOne) == TASK_STATE.FINISHED_SUCCESSFULLY)
  }

  it should "reply to the sender if predecessors are successfully completed" in {
    TaskCache.updateTaskState(tid, taskName, TASK_STATE.DORMANT)
    TaskCache.updateTaskState(tid, predecessorOne, TASK_STATE.FINISHED_SUCCESSFULLY)
    TaskCache.updateTaskState(tid, predecessorTwo, TASK_STATE.FINISHED_SUCCESSFULLY)
    taskMonitor ! TaskMonitorMessage(tid, taskName, dagName)
    Thread.sleep(testTimeout)
    assert(TaskCache.getTaskState(tid, taskName) == TASK_STATE.FINISHED_SUCCESSFULLY)

  }

  it should "reply the sender if a predecessor is unsuccessfully completed, and another successful" in {
    TaskCache.updateTaskState(tid, taskName, TASK_STATE.DORMANT)
    TaskCache.updateTaskState(tid, predecessorOne, TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, predecessorTwo, TASK_STATE.FINISHED_SUCCESSFULLY)
    taskMonitor ! TaskMonitorMessage(tid, taskName, dagName)
    Thread.sleep(testTimeout)
    assert(TaskCache.getTaskState(tid, taskName) == TASK_STATE.FINISHED_SUCCESSFULLY)
  }

  it should "reply to the sender if both predecessors are unsuccessfully completed" in {
    TaskCache.updateTaskState(tid, taskName, TASK_STATE.DORMANT)
    TaskCache.updateTaskState(tid, predecessorOne, TASK_STATE.FINISHED_UNSUCCESSFULLY)
    TaskCache.updateTaskState(tid, predecessorTwo, TASK_STATE.FINISHED_UNSUCCESSFULLY)
    taskMonitor ! TaskMonitorMessage(tid, taskName, dagName)
    Thread.sleep(testTimeout)
    assert(TaskCache.getTaskState(tid, taskName) == TASK_STATE.FINISHED_SUCCESSFULLY)
  }
}
