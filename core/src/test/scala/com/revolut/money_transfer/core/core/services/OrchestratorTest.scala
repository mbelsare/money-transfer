
package com.revolut.money_transfer.core.core.services

import java.io.File
import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core._
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.{ConfigFactory, DagFactory}
import com.revolut.money_transfer.core.utils.{RevolutAppConfig, APILogger, SpecReader}
import com.typesafe.config.Config
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec}

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.duration._

class OrchestratorTest extends FlatSpec with BeforeAndAfterAll with BeforeAndAfter {

  val configFile = "core/src/test/resources/conf/app_route_orchestrator.json"
  val terminateExecutionConfigFile = "core/src/test/resources/conf/app_route_orchestrator_terminate_actors.json"
  val jsonConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val terminateExecutionConfig: RouteConfig = SpecReader.load[RouteConfig](terminateExecutionConfigFile)
  val appConfig: Config = ConfigFactory
    .populateConfig(new File("core/src/test/resources/conf/sample_app_test.conf"), jsonConfig.actors)
  val testTimeout = 1000
  private implicit val timeout: Timeout = Timeout(1.second)
  val argsMap: MutableMap[String, Any] = MutableMap[String, Any]()
  APILogger.configure(appConfig)
  APILogger.setConstantTags()

  implicit var system: ActorSystem = ActorSystem(jsonConfig.actor_system_name, appConfig)
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  DagFactory.initializeDags(jsonConfig.dags)
  var dag: Dag = DagCache.get(jsonConfig.dags.head.name).get
  var dagId: String = dag.name
  var orchestrator: ActorRef = Orchestrator.createActor(system, 1)
  var tid: UUID = UUID.randomUUID()

  class MockActor extends Actor {
    override def receive: Receive = {
      case _ =>
    }
  }

  after {
    ConfigCache.clear()
    ErrorCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  override def afterAll(): Unit = {
    system.terminate()
    ConfigCache.clear()
    ErrorCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
  }

  behavior of "Orchestrator"

  it should "return the correct ActorRef" in {
    assert(Orchestrator.actorPath == "/user/Scheduler/Orchestrator")
  }

  it should "populate task cache for all the start nodes in the dag" in {
    DagFactory.initializeDags(jsonConfig.dags)
    jsonConfig.actors.foreach(a => TaskCache.add(new Task(tid, a.name)))

    orchestrator ! ExecuteWorkflow(dagId, tid)
    Thread.sleep(testTimeout)
    assert(dag.nodes.forall(e => TaskCache.get(tid, e).isDefined))
  }

  it should "not send a response if any node is not complete" in {
    DagFactory.initializeDags(jsonConfig.dags)
    ErrorCache.init(tid)
    RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))

    jsonConfig.actors.foreach(a => {
      TaskCache.add(new Task(tid, a.name))
      TaskCache.updateTaskState(tid, a.name, TASK_STATE.FINISHED_SUCCESSFULLY)
    })
    TaskCache.updateTaskState(tid, "a", TASK_STATE.RUNNING)

    orchestrator ! ExecutorResult("e", tid, dagId)
    Thread.sleep(testTimeout)
    assert(RouteCache.get(tid).get.state == ROUTE_STATE.INCOMPLETE)
  }

  it should "return a successful route upon the final executor's completion" in {
    DagFactory.initializeDags(jsonConfig.dags)
    ErrorCache.init(tid)
    RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))
    RouteCache.updateRouteState(tid, ROUTE_STATE.INCOMPLETE)
    jsonConfig.actors.foreach(a => {
      TaskCache.add(new Task(tid, a.name))
      orchestrator ! ExecutorResult(a.name, tid, dagId)
    })
    Thread.sleep(testTimeout)

    assert(RouteCache.get(tid).get.state == ROUTE_STATE.COMPLETE)
  }

  it should "mark each actor as completed upon receiving ExecutorResult" in {
    DagFactory.initializeDags(jsonConfig.dags)
    ErrorCache.init(tid)
    RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))
    RouteCache.updateRouteState(tid, ROUTE_STATE.INCOMPLETE)
    jsonConfig.actors.foreach(a => {
      TaskCache.add(new Task(tid, a.name))
      orchestrator ! ExecutorResult(a.name, tid, dagId)
    })
    Thread.sleep(testTimeout)

    assert(jsonConfig.actors.forall(a => TaskCache(tid, a.name).state == TASK_STATE.FINISHED_SUCCESSFULLY))
//    system.terminate()
  }

  it should "terminate execution if an actor is specified as a terminate_request_on_failure_actor" in {
    tid = UUID.randomUUID()
    system = ActorSystem(terminateExecutionConfig.actor_system_name, appConfig)
    orchestrator = Orchestrator.createActor(system, 1)
    DagFactory.initializeDags(terminateExecutionConfig.dags)
    ErrorCache.init(tid)
    RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))
    dag = DagCache.get(terminateExecutionConfig.dags.head.name).get
    dagId = dag.name

    dag.nodes.foreach(a => TaskCache.add(new Task(tid, a)))
    orchestrator ! ExecuteWorkflow(dagId, tid)
    orchestrator ! ExecutorResult("a", tid, dagId)
    orchestrator ! ExecutorError("actor b failed", 500, "b", tid, dagId)
    Thread.sleep(testTimeout)

    val tasks = dag.nodes.map(a => TaskCache(tid, a).state)
    assert(tasks.contains(TASK_STATE.TERMINATED))
    assert(RouteCache.get(tid).get.state == ROUTE_STATE.COMPLETE)
//    system.terminate()
  }

  it should "stop execution for all subsequent plugins to an actor if an actor is specified as a " +
    "terminate_request_on_failure_actor and do not throw exception for cache misses after cache clearing" in {
    tid = UUID.randomUUID()
    system = ActorSystem(terminateExecutionConfig.actor_system_name, appConfig)
    orchestrator = Orchestrator.createActor(system, 1)
    DagFactory.initializeDags(terminateExecutionConfig.dags)
    ErrorCache.init(tid)
    RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))
    dag = DagCache.get(terminateExecutionConfig.dags(1).name).get
    dagId = dag.name

    dag.nodes.foreach(a => TaskCache.add(new Task(tid, a)))
    orchestrator ! ExecuteWorkflow(dagId, tid)
    orchestrator ! ExecutorResult("e", tid, dagId)
    Thread.sleep(testTimeout)
    orchestrator ! ExecutorError("actor g failed", 500, "g", tid, dagId)
    orchestrator ! ExecutorResult("h", tid, dagId)
    Thread.sleep(testTimeout)
    TransactionMonitor.clearCaches(tid, dagId)  //mimic clearing cache behavior

    assert(TaskCache.get(tid, "e").isEmpty)
    assert(TaskCache.get(tid, "f").isEmpty)
    assert(TaskCache.get(tid, "g").isEmpty)
    assert(TaskCache.get(tid, "h").isEmpty)
//    system.terminate()
  }

    it should "return an incomplete route state if some actors are completed but one actor is not" in {
      tid = UUID.randomUUID()
      DagFactory.initializeDags(jsonConfig.dags)
      ErrorCache.init(tid)
      RouteCache.add(tid, new Route(tid, system.actorOf(Props(new MockActor))))
      dag = DagCache.get(jsonConfig.dags.head.name).get
      dagId = dag.name

      jsonConfig.actors.foreach(a => TaskCache.add(new Task(tid, a.name)))
      orchestrator ! ExecuteWorkflow(dagId, tid)
      Thread.sleep(testTimeout)
      TaskCache.updateTaskState(tid, "d", TASK_STATE.RUNNING)
      orchestrator ! ExecutorResult("a", tid, dagId)
      orchestrator ! ExecutorResult("b", tid, dagId)
      orchestrator ! ExecutorResult("c", tid, dagId)

      Thread.sleep(testTimeout)
      assert(RouteCache.get(tid).get.state == ROUTE_STATE.INCOMPLETE)
      system.terminate()
    }
}
