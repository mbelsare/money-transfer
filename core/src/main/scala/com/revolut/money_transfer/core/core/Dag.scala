package com.revolut.money_transfer.core.core

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge

class Dag(private val graph: Graph[String, DiEdge],
          val name: String,
          val startup_actors: Set[String] = Set[String](),
          val terminate_request_on_failure_actors: Set[String] = Set[String]()) {

  //Returns nodes with zero in-degree
  val startNodes: Set[String] = graph.nodes.filter(_.diPredecessors.isEmpty).map(_.toOuter).toSet.diff(startup_actors)

  private def get(node: String): graph.NodeT = (graph get node).asInstanceOf[graph.NodeT]

  def isCyclic: Boolean = graph.isCyclic

  def predecessors(node: String): Set[String] = get(node).diPredecessors.map(_.toOuter)

  def successors(node: String): Set[String] = get(node).diSuccessors.map(_.toOuter)

  def nodes: Set[String] = graph.nodes.toOuter

}
