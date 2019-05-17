package com.revolut.money_transfer.core.core.factory

import com.revolut.money_transfer.core.core.Dag
import com.revolut.money_transfer.core.core.cache.DagCache
import com.revolut.money_transfer.core.case_classes.ScalaGraphJSON
import com.typesafe.scalalogging.LazyLogging
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization.write

import scala.util.{Failure, Success, Try}
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.io.json._
import scalax.collection.io.json.descriptor.predefined.{Di, DiHyper}


/*
 * The DagFactory initializes the DAGs used for orchestration by parsing the application's configuration JSON
 * The JSON is a flat representation of a graph
 * Each actor node is defined and assigned a unique identifying name within the JSON,
 * which is used as a key to defining directed edges
 * Each edge is defined independently
 * The JSON also assigns a unique ID to the DAG
 */
object DagFactory extends LazyLogging {

  //Required for JSON conversion
  implicit val formats: DefaultFormats = DefaultFormats

  //Node descriptor for Scala Graph JSON
  val stringNodeDescriptor = new NodeDescriptor[String](typeId = "ActorNodes") {
    def id(node: Any) = node.asInstanceOf[String]
  }

  //DAG descriptor for Scala Graph JSON
  val stringDagDescriptor = new Descriptor[String](
    defaultNodeDescriptor = stringNodeDescriptor,
    defaultEdgeDescriptor = DiHyper.descriptor[String](),
    namedNodeDescriptors = Seq(stringNodeDescriptor),
    namedEdgeDescriptors = Seq(Di.descriptor[String]())
  )

  def initializeDags(dags: List[ScalaGraphJSON]): Boolean = {
    dags.forall(d =>
      Try(DagCache.add(new Dag(Graph.fromJson[String, DiEdge](write(d), stringDagDescriptor),
        d.name,
        d.startup_only_nodes,
        d.terminate_request_on_failure_actors))) match {
        case Success(v) => {
          val edgesContainsStartupActors = {
            val startupActors = DagCache(d.name).startup_actors
            d.edges("DiEdge")
              .exists(edgePair => startupActors.contains(edgePair("n1")) || startupActors.contains(edgePair("n2")))
          }

          if (edgesContainsStartupActors) {
            logger.error(s"Dag ${d.name} contains startup_only actors in its edges")
            false
          } else if (DagCache(d.name).isCyclic) {
            logger.error(s"Dag ${d.name} is cyclic")
            false
          } else {
            logger.debug(s"Dag ${d.name} initialized")
            true
          }
        }
        case Failure(e) => {
          logger.error(s"Dag ${d.name} failed to initialized with error ${e.getMessage}")
          false
        }
      })
  }

}
