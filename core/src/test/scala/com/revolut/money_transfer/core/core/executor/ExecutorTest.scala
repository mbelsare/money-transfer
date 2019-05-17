
package com.revolut.money_transfer.core.core.executor

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.core.{ROUTE_STATE, Route, TASK_STATE, Task}
import com.revolut.money_transfer.core.utils.{PluginLibJar, RevolutAppConfig, SpecReader}
import com.revolut.money_transfer.core.core.services.{Scheduler, TaskMonitor}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Await

class ExecutorTest extends FlatSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll() {
    Scheduler.shutdown(system)
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  ConfigCache.clear()
  DagCache.clear()
  MessageCache.clear()
  RouteCache.clear()
  TaskCache.clear()
  val configFile = "core/src/test/resources/conf/app_route_executor_actor.json"
  var routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val appConfig = ConfigFactory.populateConfig(
    new File("core/src/test/resources/conf/sample_app_test.conf"), routeConfig.actors)
  val targetEnv = appConfig.getString("targetEnv").toLowerCase
  val configMap = appConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  val dagId = routeConfig.dags.head.name
  val featureLibClassFile: String = "core/external_libs/plugins-test.jar"
  val classLoader = PluginLibJar.load(featureLibClassFile)
  val tid = UUID.randomUUID
  val dagJson = routeConfig.dags.head
  val args = MutableMap[String, Any]("accountId" -> "1234")
  val timeoutInSeconds: Int = 10
  implicit val timeout = Timeout(timeoutInSeconds, TimeUnit.SECONDS)
  implicit var system = ActorSystem(routeConfig.actor_system_name, appConfig)
  implicit val materializer = ActorMaterializer()
  val testTimeout = 1000
  val startup = Scheduler.startup(system,
    timeout,
    serviceConfig.toMap,
    routeConfig,
    classLoader)
  ErrorCache.init(tid)

  it must "send a message to the scheduler after initialization" in {
    assert(startup.isInstanceOf[StartupResult])
  }

  it must "reply with execution results when passed a specific function implementation in " +
    "featurelib and one next actor object" in {
    MessageCache.clear()
    MessageCache.add(tid, args.clone)
    TaskCache.clear()
    dagJson.nodes._2.foreach(e => TaskCache.add(new Task(tid, e)))
    TaskCache.updateTaskState(tid, "flattenBody", TASK_STATE.FINISHED_SUCCESSFULLY)
    val tasks = dagJson.nodes._2.map(e => TaskCache(tid, e))
    RouteCache.clear()
    RouteCache.add(
      new Route(tid, Await.result(system.actorSelection(Scheduler.actorPath).resolveOne, timeout.duration)))
    val route = RouteCache(tid)

    system.actorSelection(TaskMonitor.actorPath) ! TaskMonitorMessage(tid, "db", dagId)
    Thread.sleep(testTimeout)
    assert(tasks.forall(e => e.state == TASK_STATE.FINISHED_SUCCESSFULLY))
    assert(route.state == ROUTE_STATE.COMPLETE)
  }

  it must "reply with execution results when passed a specific function implementation in " +
    "featurelib and no next actor object" in {
    MessageCache.clear()
    MessageCache.add(tid, args.clone)
    TaskCache.clear()
    dagJson.nodes._2.foreach(e => TaskCache.add(new Task(tid, e)))
    val tasks = dagJson.nodes._2.map(e => TaskCache(tid, e))
    RouteCache.clear()
    RouteCache.add(
      new Route(tid, Await.result(system.actorSelection(Scheduler.actorPath).resolveOne, timeout.duration)))
    val route = RouteCache(tid)

    system.actorSelection(TaskMonitor.actorPath) ! TaskMonitorMessage(tid, "flattenBody", dagId)
    Thread.sleep(testTimeout)
    assert(tasks.head.state == TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(route.state == ROUTE_STATE.COMPLETE)
  }

  it must "reply with execution results when a task fails and there are no successors" in {
    MessageCache.clear()
    MessageCache.add(tid, args.clone)
    TaskCache.clear()
    dagJson.nodes._2.foreach(e => TaskCache.add(new Task(tid, e)))
    TaskCache.updateTaskState(tid, "flattenBody", TASK_STATE.FINISHED_SUCCESSFULLY)
    val dbTask = TaskCache(tid, "db")
    val flattenTask = TaskCache(tid, "flattenBody")
    RouteCache.clear()
    RouteCache.add(
      new Route(tid, Await.result(system.actorSelection(Scheduler.actorPath).resolveOne, timeout.duration)))
    val route = RouteCache(tid)
    val badActor = system.actorOf(Props(new Executor(classLoader, "bad.path", "db", Map(), 500)))

    badActor ! ExecutePlugin(tid, dagId)
    Thread.sleep(testTimeout)
    assert(dbTask.state == TASK_STATE.FINISHED_UNSUCCESSFULLY)
    assert(flattenTask.state == TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(route.state == ROUTE_STATE.COMPLETE)
  }

  it must "reply with execution results when a task fails and there are is one successor" in {
    MessageCache.clear()
    MessageCache.add(tid, args.clone)
    TaskCache.clear()
    dagJson.nodes._2.foreach(e => TaskCache.add(new Task(tid, e)))
    val dbTask = TaskCache(tid, "db")
    val flattenTask = TaskCache(tid, "flattenBody")
    RouteCache.clear()
    RouteCache.add(
      new Route(tid, Await.result(system.actorSelection(Scheduler.actorPath).resolveOne, timeout.duration)))
    val route = RouteCache(tid)
    val badActor = system.actorOf(Props(new Executor(classLoader, "bad.path", "flattenBody", Map(), 500)))

    badActor ! ExecutePlugin(tid, dagId)
    Thread.sleep(testTimeout)
    assert(dbTask.state == TASK_STATE.FINISHED_SUCCESSFULLY)
    assert(flattenTask.state == TASK_STATE.FINISHED_UNSUCCESSFULLY)
    assert(route.state == ROUTE_STATE.COMPLETE)
  }
}
