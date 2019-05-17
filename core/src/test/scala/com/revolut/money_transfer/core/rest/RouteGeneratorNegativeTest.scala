
package com.revolut.money_transfer.core.rest

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{BuildRoute, RouteConfig}
import com.revolut.money_transfer.core.core.ActorInterface
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.utils.{PluginLibJar, RevolutAppConfig, APILogger, SpecReader}
import com.revolut.money_transfer.core.core.services.Scheduler
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scalaj.http.Http

class RouteGeneratorNegativeTest extends FlatSpec with ActorInterface with BeforeAndAfterAll {

  val configFile = "core/src/test/resources/conf/app_negative_route_test.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val host: String = routeConfig.host
  val port: Int = routeConfig.port
  var server: Option[RestServer] = None
  val pluginLibClassFile: String = "core/external_libs/revolut-plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(pluginLibClassFile)
  val connectionTimeout: Int = 20000

  val appConfig = ConfigFactory.populateConfig(new File("core/src/test/resources/conf/sample_app_test.conf"),
    routeConfig.actors)
  APILogger.configure(appConfig)
  APILogger.setConstantTags()
  val targetEnv = appConfig.getString("targetEnv").toLowerCase
  val configMap = appConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  implicit val system: ActorSystem = ActorSystem(routeConfig.actor_system_name, appConfig)
  implicit val materializer = ActorMaterializer()
  override implicit val ec: ExecutionContext = system.dispatcher
  val timeoutInSeconds = 15
  override implicit val timeout: Timeout = Timeout(timeoutInSeconds, TimeUnit.SECONDS)
  implicit val log = Logging.getLogger(system, this)
  Scheduler.startup(system, timeout, serviceConfig.toMap, routeConfig, classLoader)

  override def beforeAll() {
    server = Option(new RestServer())
    val route = Await.result(system.actorSelection(RouteGenerator.actorPath) ? BuildRoute(routeConfig.routes),
      timeout.duration).asInstanceOf[Route]
    server.get.start(host, port, route)
    Thread.sleep(200)
  }

  override def afterAll() {
    server.get.stop()
    Scheduler.shutdown(system)
    ConfigCache.clear()
    DagCache.clear()
    MessageCache.clear()
    RouteCache.clear()
    TaskCache.clear()
    RevolutAppConfig.clear()
    system.terminate()
  }

  it should "return internal server error" in {
    // Test the route
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/101/money-transfer")
      .postData("")
      .header("Charset", "UTF-8")
      .timeout(connectionTimeout,connectionTimeout)
    assert(httpRequest.asString.code == StatusCodes.InternalServerError.intValue)
    assert(TaskCache.isEmpty)
    assert(RouteCache.isEmpty)
  }
}
