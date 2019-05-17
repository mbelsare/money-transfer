package com.revolut.money_transfer.core.rest

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.revolut.money_transfer.core.case_classes.{BuildRoute, RouteConfig}
import com.revolut.money_transfer.core.core.ActorInterface
import com.revolut.money_transfer.core.core.cache._
import com.revolut.money_transfer.core.core.factory.ConfigFactory
import com.revolut.money_transfer.core.utils.{APILogger, PluginLibJar, RevolutAppConfig, SpecReader}
import com.revolut.money_transfer.core.core.services.Scheduler
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext}
import scalaj.http.Http

class RouteGeneratorTest extends FlatSpec with ActorInterface with BeforeAndAfterAll with BeforeAndAfter {

  val timeoutInSeconds = 15
  val sleepTime = 200
  val configFile = "core/src/test/resources/conf/app_route.json"
  val routeConfig: RouteConfig = SpecReader.load[RouteConfig](configFile)
  val host: String = routeConfig.host
  val port: Int = routeConfig.port
  var server: Option[RestServer] = None
  val pluginLibClassFile: String = "core/external_libs/revolut-plugins-test.jar"
  val classLoader: URLClassLoader = PluginLibJar.load(pluginLibClassFile)

  val appConfig = ConfigFactory.populateConfig(
    new File("core/src/test/resources/conf/sample_app_test.conf"), routeConfig.actors)
  APILogger.configure(appConfig)
  APILogger.setConstantTags()
  val targetEnv = appConfig.getString("targetEnv").toLowerCase
  val configMap = appConfig.getObject(targetEnv).unwrapped().asScala
  val serviceConfig = configMap("service").asInstanceOf[java.util.HashMap[String, AnyRef]].asScala

  implicit val system: ActorSystem = ActorSystem(routeConfig.actor_system_name, appConfig)
  implicit val materializer = ActorMaterializer()
  override implicit val timeout: Timeout = Timeout(timeoutInSeconds, TimeUnit.SECONDS)
  override implicit val ec: ExecutionContext = system.dispatcher
  implicit val log = Logging.getLogger(system, this)

  Scheduler.startup(system, timeout, serviceConfig.toMap, routeConfig, classLoader)

  override def beforeAll() {
    server = Option(new RestServer())
    val route = Await.result(system.actorSelection(RouteGenerator.actorPath) ? BuildRoute(routeConfig.routes),
      timeout.duration).asInstanceOf[Route]
    server.get.start(host, port, route)
    Thread.sleep(sleepTime)
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

  def assertEmptyCaches(): Unit = {
    assert(TaskCache.isEmpty)
    assert(MessageCache.isEmpty)
    assert(RouteCache.isEmpty)
  }

  behavior of "Route built by RouteGenerator"
  it should "return 200 status code for health check on the correct endpoint" in {
    val httpRequest = Http(s"http://$host:$port/${routeConfig.routes.head.health_check_path_prefixes.mkString("/")}")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 200)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body == """{"health-check":"OK"}""")
    assertEmptyCaches()
  }

  it should "return 404 status code for health check on an incorrect endpoint" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/money-transfer/health-c")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 404)
    assert(httpRequest.asString.body == "The requested resource could not be found.")
    assertEmptyCaches()
  }

  it should "return 405 status code when making a get request on a post endpoint and not add to message cache" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/101/money-transfer")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 405)
    assert(httpRequest.asString.body == "HTTP method not allowed, supported methods: POST")
    assertEmptyCaches()
  }

  it should "return 404 status code when calling an invalid post endpoint and not add to message cache" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/101")
      .postData("""{"a":"b"}""")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 404)
    assert(httpRequest.asString.body == "The requested resource could not be found.")
    assertEmptyCaches()
  }

  it should "return 400 status code when post data is not a valid json" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/101/money-transfer")
      .postData("""{ "some" "JSON source" }""")
      .header("Content-Type", "application/json")

    assert(httpRequest.asString.code == 400)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains("""{"error":"Invalid JSON Body"}"""))
    assertEmptyCaches()
  }

  it should "return 200 status code when post data is a valid json and consume path variables" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": 200 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString

    assert(response.code == 200)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""withdrawalAccountBalance":700.0"""))
    assert(response.body.contains(""""depositAccountBalance":3700.0"""))
    assert(response.body.contains(""""status":"Success""""))
    assertEmptyCaches()
  }

  it should "return 200 status code when post data is a valid json and RouteGenerator consumes path variables " +
    "and a field in required_fields is not present in path variables or body and dag does not have rename fields" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1221/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": 200, "dagKey": "requiredFieldMissingDag" }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString

    assert(response.code == 200)
    assert(response.contentType.get == "application/json")
    assert(!response.body.contains(""""accountId":"1221""""))
    assert(!response.body.contains(""""accountNumber":"1221""""))
    assert(response.body.contains(""""withdrawalAccountBalance":300.0"""))
    assert(response.body.contains(""""body.depositAccountId":"1321""""))
    //the server is run only once before execution of all tests and shuts down after all are complete
    assert(response.body.contains(""""depositAccountBalance":3900.0"""))
    assertEmptyCaches()
  }

  it should "return 200 status code when post data is a valid json and consume path variables " +
    "and dag has rename fields included" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": 200 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString
    assert(response.code == 200)
    assert(response.contentType.get == "application/json")
    assert(!response.body.contains(""""accountNumber":"1234""""))
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"1321""""))
    assert(response.body.contains(""""withdrawalAccountBalance":500.0"""))
    assert(response.body.contains(""""depositAccountBalance":4100.0"""))
    assert(response.body.contains(""""transactionIdentifier""""))
    assert(!response.body.contains(""""errors":[]"""))
    assertEmptyCaches()
  }

  it should "return 200 status code when post data is a valid json and dag has rename fields included " +
    "and one field 'x' in required_fields is not present in request and/or response body" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/some-endpoint")
      .postData("""{ "depositAccountId": "1321", "transferAmount": 100.5 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString
    assert(response.code == 200)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountNumber":"1234""""))
    assert(!response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"1321""""))
    assert(response.body.contains(""""withdrawalAccountBalance":399.5"""))
    assert(response.body.contains(""""depositAccountBalance":4200.5"""))
    assert(!response.body.contains(""""x":"b"""))
    assert(!response.body.contains(""""errors":[]"""))
    assertEmptyCaches()
  }

  it should "return 400 status code when post data is empty" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 400)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains(""""accountId":"1234""""))
    assert(httpRequest.asString.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 403 status code when post data is valid but withdrawal account has insufficient funds" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": 1000 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString
    assert(response.code == 403)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"1321""""))
    // 'withdrawalAccountBalance' is only responded if the 'withdraw' operation succeeded else it does not populate.
    assert(!response.body.contains(""""withdrawalAccountBalance""""))
    assert(response.body.contains(""""status":"Failed""""))
    assert(response.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 403 status code and roll back amount to withdrawal account when post data is valid " +
    "but deposit account is in CLOSED state" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "4321", "transferAmount": 100 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString
    assert(response.code == 403)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"4321""""))
    assert(response.body.contains(""""withdrawalAccountBalance":399.5"""))
    assert(response.body.contains(""""status":"Failed""""))
    assert(response.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 400 status code and roll back amount to withdrawal account when post data is valid " +
    "but withdrawal account details and deposit account details are same" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1234", "transferAmount": 250 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString
    assert(response.code == 400)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"1234""""))
    assert(response.body.contains(""""withdrawalAccountBalance":399.5"""))
    assert(response.body.contains(""""status":"Failed""""))
    assert(response.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 400 status code when post data does not have transfer amount for processing transfer" +
    " but withdrawal account number and deposit account number are valid" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "a": "b" }""")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 400)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains(""""accountId":"1234""""))
    assert(httpRequest.asString.body.contains(""""depositAccountId":"1321""""))
    assert(httpRequest.asString.body.contains(""""status":"Failed""""))
    assert(httpRequest.asString.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 404 status code and roll back withdrawal amount when deposit account number does not exist" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1111", "transferAmount": 100 }""")
      .header("Charset", "UTF-8")

    val response = httpRequest.asString

    assert(response.code == 404)
    assert(response.contentType.get == "application/json")
    assert(response.body.contains(""""accountId":"1234""""))
    assert(response.body.contains(""""depositAccountId":"1111""""))
    assert(response.body.contains(""""withdrawalAccountBalance":399.5"""))
    assert(response.body.contains(""""status":"Failed""""))
    assert(response.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 403 status code when post data is valid but withdrawal account is in a HOLD state" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/2001/money-transfer")
      .postData("""{ "depositAccountId": "1234", "transferAmount": 100 }""")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 403)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains(""""accountId":"2001""""))
    assert(httpRequest.asString.body.contains(""""depositAccountId":"1234""""))
    assert(httpRequest.asString.body.contains(""""status":"Failed""""))
    assert(httpRequest.asString.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 400 status code when post data is invalid as transfer amount is negative" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": -200 }""")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 400)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains(""""accountId":"1234""""))
    assert(httpRequest.asString.body.contains(""""depositAccountId":"1321""""))
    assert(httpRequest.asString.body.contains(""""status":"Failed""""))
    assert(httpRequest.asString.body.contains(""""errors""""))
    assertEmptyCaches()
  }

  it should "return 400 status code when post data is invalid as transfer amount is not a double value" in {
    val httpRequest = Http(s"http://$host:$port/accounts/transfers/1234/money-transfer")
      .postData("""{ "depositAccountId": "1321", "transferAmount": "200" }""")
      .header("Charset", "UTF-8")

    assert(httpRequest.asString.code == 400)
    assert(httpRequest.asString.contentType.get == "application/json")
    assert(httpRequest.asString.body.contains(""""accountId":"1234""""))
    assert(httpRequest.asString.body.contains(""""depositAccountId":"1321""""))
    assert(httpRequest.asString.body.contains(""""status":"Failed""""))
    assert(httpRequest.asString.body.contains(""""errors""""))
    assertEmptyCaches()
  }

}
