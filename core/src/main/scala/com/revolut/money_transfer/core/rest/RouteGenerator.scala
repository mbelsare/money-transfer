
package com.revolut.money_transfer.core.rest

import java.util.UUID
import java.util.regex.Pattern

import akka.ConfigurationException
import akka.actor.{Actor, ActorRef, ActorRefFactory, ActorSystem, Props, SupervisorStrategy}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives.{pathPrefix, _}
import akka.http.scaladsl.server.{Directive0, ExceptionHandler, Route}
import akka.pattern.{CircuitBreaker, ask}
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.revolut.money_transfer.commons.StatusCodes
import com.revolut.money_transfer.core.case_classes._
import com.revolut.money_transfer.core.core.ActorInterface
import com.revolut.money_transfer.core.core.cache.{DagCache, ErrorCache, MessageCache}
import com.revolut.money_transfer.core.utils.{APIInterfaceHandler, RevolutAppConfig, APILogger}
import com.fasterxml.jackson.core.JsonParseException
import com.revolut.money_transfer.core.case_classes.RouteConfig
import com.revolut.money_transfer.core.core.services.Scheduler
import com.typesafe.scalalogging.LazyLogging
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, _}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scalaj.http.HttpStatusException
import scala.collection.mutable.ListBuffer

private class RouteGenerator(val actorSystem: ActorSystem,
                             val actorSystemTimeout: Timeout,
                             val routeConfig: RouteConfig,
                             val routeActor: ActorRef) extends ActorInterface with Actor with LazyLogging {

  override implicit val system = this.actorSystem
  override implicit val timeout = this.actorSystemTimeout
  override implicit val ec: ExecutionContext = system.dispatcher
  implicit val formats = DefaultFormats

  lazy val breaker = new CircuitBreaker(
    system.scheduler,
    maxFailures = RevolutAppConfig.config.get.getOrElse("circuitBreakerMaxRouteFailures", 10).asInstanceOf[Int],
    callTimeout = RevolutAppConfig.config.get.getOrElse("circuitBreakerRouteCallTimeout", 20).asInstanceOf[Int].seconds,
    resetTimeout = RevolutAppConfig.config.get.getOrElse("circuitBreakerResetRouteTimeout", 10)
      .asInstanceOf[Int].seconds)
    .onOpen(logger.debug(s"RouteGenerator CircuitBreaker open"))
    .onClose(logger.debug(s"RouteGenerator CircuitBreaker closed"))
    .onHalfOpen(logger.debug(s"RouteGenerator CircuitBreaker half-open"))

  /**
    * Builds an akka-http route tree
    * Example route tree, pre-order traversal:
    *
    * Add exception handler to route
    * └── Add request UUID to header
    *     ├── Add request body to MessageCache
    *     │   ├── First path prefix matches first prefix in first endpoint, if variable add to MessageCache
    *     │   │    └── Second path prefix matches second path prefix in first endpoint, if variable add to MessageCache
    *     │   │        └── Third path prefix matches third path prefix in first route, if variable add to MessageCache
    *     │   │            └── Path end or single slash
    *     │   │                └── Call Orchestrator
    *     │   │                    └── Reply with result to sender
    *     │   └── First path prefix matches first prefix in second endpoint, if variable add to MessageCache
    *     │       └── Second path prefix matches second path prefix in second endpoint, if variable add to MessageCache
    *     │           └── Path end or single slash
    *     │               └── Call Orchestrator
    *     │                   └── Reply with result to sender
    *     └── First path prefix matches first prefix in first health check endpoint
    *         └── Second path prefix matches second path prefix in second health check endpoint
    *             └── Path end or single slash
    *                 └── Reply with result to sender
    *
    * If no terminal nodes of the tree are reached, akka-http will send an appropriate response
    *
    * @param routes details about API and health check endpoints that must be built
    * @return the akka-http route tree that was built
    */
  def buildRoute(routes: List[RouteDetails]): Route = {
    logger.debug("Received request to build route")
    handleExceptions(exceptionHandler) {
      addRequestUUID() {
        addBody {
          buildApi {
            routes
          }
        } ~ extractRequest { request =>
          MessageCache.delete(UUID.fromString(request.getHeader("transactionId").get().value()))
          buildHealthCheck {
            routes
          }
        }
      }
    }
  }

  /**
    * Recursively builds health check endpoints depth-first
    *
    * @param routeDetails details about health check endpoints that must be built
    * @return route tree of health check endpoints
    */
  private def buildHealthCheck(routeDetails: List[RouteDetails]): Route = {
    if (routeDetails.lengthCompare(1) == 0)
      buildHealthCheckEndpoint(routeDetails.head.health_check_path_prefixes)
    else
      buildHealthCheck(routeDetails.tail) ~ buildHealthCheckEndpoint(routeDetails.head.health_check_path_prefixes)
  }

  /**
    * Recursively builds one health check endpoint depth-first
    *
    * @param prefixes path prefixes of the health check endpoint that must be built
    * @return route tree for one health check endpoint
    */
  private def buildHealthCheckEndpoint(prefixes: List[String]): Route = {
    if (prefixes.isEmpty)
      pathEndOrSingleSlash {
        get {
          complete {
            HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentType(MediaTypes.`application/json`),
              """{"health-check":"OK"}"""))
          }
        }
      }
    else pathPrefix(prefixes.head)(buildHealthCheckEndpoint(prefixes.tail))
  }

  /**
    * Recursively builds API endpoints depth-first
    *
    * @param routeDetails details about API endpoints that must be built
    * @return route tree of API endpoints
    */
  def buildApi(routeDetails: List[RouteDetails]): Route = {
    if (routeDetails.lengthCompare(1) == 0) {
      buildApiEndpoint(routeDetails.head.path_prefixes, routeDetails.head.http_verb,
        routeDetails.head.default_dag)
    }
    else {
      buildApi(routeDetails.tail) ~
        buildApiEndpoint(routeDetails.head.path_prefixes, routeDetails.head.http_verb,
          routeDetails.head.default_dag)
    }
  }

  private def buildApiEndpoint(prefixes: List[String], httpVerb: String,
                                          dagName: String): Route = {
    if (prefixes.isEmpty)
      pathEndOrSingleSlash {
        addHttpVerb(generateRequest(dagName), httpVerb)
      }
    else addPrefix(prefixes.head, buildApiEndpoint(prefixes.tail, httpVerb, dagName))
  }

  /**
    * @param route input route
    * @param httpVerb HTTP request method
    * @return route tree with http request method prepended
    */
  private def addHttpVerb(route: Route, httpVerb: String): Route = {

    httpVerb.toLowerCase match {
      case "get" => get(route)
      case "post" => post(route)
      case "put" => put(route)
      case "delete" => delete(route)
      case _ => get(route)
    }
  }

  /**
    * If versioning enabled, this function created a dagname dynamically from the version in header
    * On boot up, Dag cache is populated with DAG information of all DAG's in JSON file
    * The function looks up into Dag Cache to get the dagName if exist else creates one
    *
    * @param request
    * @param transactionId
    * @param dagName
    * @return
    */
  private def extractDagName(request: HttpRequest, transactionId: UUID,
                             dagName: String): String = {
      val altDag = matchDagSelectionKeys(transactionId, dagName)
      if (DagCache.contains(altDag)) altDag else dagName
  }

  /***
    * Matches dag selection keys to body parameters to select a dag. The default dag will be used
    * if dag_selection_keys is not defined, selection_keys are empty, or the body request doesn't contain
    * any of the selection_keys. If more than one set of selection keys clash, it will return 400. If no matches
    * are found, it will select the default dag.
    * @param transactionId
    * @param dagName
    * @return
    */
  private def matchDagSelectionKeys(transactionId: UUID, dagName: String): String = {
    if (routeConfig.dag_selection_keys.isEmpty || routeConfig.dag_selection_keys.get.selection_keys.isEmpty) dagName
    else {
      val bodyKeys = MessageCache(transactionId)
        .filter(k => routeConfig.dag_selection_keys.get.selection_keys.map(key => "body." + key).contains(k._1))

      if (bodyKeys.isEmpty) dagName

      else {
        val matches = ListBuffer[String]()
        var allMatches = true
        for (selectionKeySet <- routeConfig.dag_selection_keys.get.values) {
          allMatches = true
          for (k <- selectionKeySet.keys) {
            if (bodyKeys("body." + k._1) != selectionKeySet.keys(k._1)) {
              allMatches = false
            }
          }

          if (allMatches) {
            matches += selectionKeySet.dag_name
          }

        }

        if (matches.size > 1) {
          throw new HttpStatusException(400,
            "Selection keys match multiple dag names: " + matches.toList.mkString(", "), dagName)
        } else if (matches.isEmpty) {
          dagName
        } else {
          matches.toList.head
        }
      }
    }
  }

  /**
    *
    * @return
    */
  override def receive: Receive = {
    case msg: BuildRoute => {
      logger.info(s"Inside the RouteGenerator Receive block for BuildRoute: ${msg.routes}")
      sender ! buildRoute(msg.routes)
    }
  }

  /**
    * Adds call to Orchestrator to route tree
    * @param dagName name of DAG this request calls
    * @return Route tree
    */
  private def generateRequest(dagName: String,
                              aPIInterfaceHandler: APIInterfaceHandler = new APIInterfaceHandler()): Route =
    extractRequest { request =>
    val transactionId = UUID.fromString(request.getHeader("transactionId").get().value())
    val validatedAltDag = extractDagName(request, transactionId, dagName)
    APILogger.setRuntimeTags(MessageCache(transactionId))
    APILogger.info(s"Starting request for transaction $transactionId at ${System.currentTimeMillis()}")
    onComplete(breaker.withCircuitBreaker(routeActor ? ExecuteWorkflow(validatedAltDag, transactionId))) {
      case Success(message) => message match {
        case response: WorkflowResult => complete {
          APILogger.info(s"Received WorkflowResult for ${response.transactionId} at ${System.currentTimeMillis()}")
          aPIInterfaceHandler.sendResponse(transactionId, response.dagName,
            response.statusCode,System.currentTimeMillis() - request.getHeader("startTime").get().value().toLong,
            MessageCache(transactionId) - "errors", routeConfig)
        }
        case response: WorkflowError => complete {
          aPIInterfaceHandler.sendResponse(transactionId, response.dagName,
            response.statusCode,System.currentTimeMillis() - request.getHeader("startTime").get().value().toLong,
            MessageCache(transactionId) + ("errors" -> ErrorCache(transactionId)), routeConfig)
        }
      }
      case Failure(failure) => complete {
        aPIInterfaceHandler.sendResponse(transactionId,
          validatedAltDag, StatusCodes.InternalServerError.intValue,
          System.currentTimeMillis() - request.getHeader("startTime").get().value().toLong,
          MessageCache(transactionId) + ("errors" -> failure.toString), routeConfig)
      }
    }
  }

  /**
    * Adds a single prefix to a route, checks for endpoint params and adds to MessageCache
    * @param prefix
    * @param route
    * @return
    */
  private def addPrefix(prefix: String, route: Route): Route = {
    if (prefix.substring(0, 2) + prefix.reverse.substring(0, 2) == "____")
      pathPrefix(Segment) { queryParam: String =>
        extractRequest { request =>
          MessageCache.append(UUID.fromString(request.getHeader("transactionId").get().value()),
            Map(prefix.slice(2, prefix.length - 2) -> queryParam))
          route
        }
      }
    else pathPrefix(prefix)(route)
  }

  /**
    * If body exists, checks if it is a valid JSON, add to MessageCache
    * @param route
    * @return
    */
  private def addBody(route: Route): Route = entity(as[String]) { input =>
    // only make one call to messagecache before calling orchestrator
    extractRequest { request =>
      val tid = UUID.fromString(request.getHeader("transactionId").get().value())
      if (!input.isEmpty)
        MessageCache.add(tid, Map("body" -> input, "transactionId" -> tid) ++
          parse(input).extract[Map[String, Any]].map(e => s"body.${e._1}" -> e._2))
      else
        MessageCache.add(tid, Map("body" -> input, "transactionId" -> tid))
      route
    }
  }

  /**
    * Adds UUID (transactionId) to request header in order to uniquely identify it
    * @return
    */
  private def addRequestUUID(): Directive0 = mapRequest { request: HttpRequest =>
    request.withHeaders(request.headers ++ Seq(RawHeader("transactionId", UUID.randomUUID().toString),
      RawHeader("startTime", System.currentTimeMillis().toString)))
  }

  /**
    * Exception handler for route
    */
  val exceptionHandler: ExceptionHandler = ExceptionHandler {
    case response: JsonParseException =>
      logger.error(s"RouteGenerator: ${response.toString}")
      complete(HttpResponse(StatusCodes.BadRequest,
        entity = HttpEntity(ContentType(MediaTypes.`application/json`), """{"error":"Invalid JSON Body"}""")))
    case response: HttpStatusException =>
      logger.error(s"RouteGenerator: ${response.toString}")
      complete(HttpResponse(StatusCodes.BadRequest,
        entity = HttpEntity(ContentType(MediaTypes.`application/json`),
          """{"error": "Invalid default dag name or incorrect api version provided in header"}""")))
    case response: Throwable => complete {
      logger.error(s"RouteGenerator: ${response.toString}")
      HttpResponse(StatusCodes.InternalServerError,
        entity = HttpEntity(ContentType(MediaTypes.`application/json`), response.getMessage))
    }
  }

}

object RouteGenerator {
  private val _name = "RouteGenerator"

  def name: String = _name

  def actorPath: String = s"${Scheduler.actorPath}/$name"

  def createActor(context: ActorRefFactory,
                  actorSystem: ActorSystem,
                  actorSystemTimeout: Timeout,
                  routeConfig: RouteConfig,
                  routeActor: ActorRef,
                  numActorInstances: Int): ActorRef = {

    val routeGenerator = Props(new RouteGenerator(actorSystem,
      actorSystemTimeout,
      routeConfig,
      routeActor))
      .withRouter(RoundRobinPool(numActorInstances, supervisorStrategy = SupervisorStrategy.defaultStrategy))

    try {
      context.actorOf(routeGenerator.withDispatcher("akka.actor.core-dispatcher"), name)
    } catch {
      case _: ConfigurationException =>
        context.actorOf(routeGenerator, name)
    }
  }
}
