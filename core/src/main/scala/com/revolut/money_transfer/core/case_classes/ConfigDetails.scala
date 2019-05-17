package com.revolut.money_transfer.core.case_classes

final case class ActorSpec(name: String,
                           class_path: String,
                           numActorInstances: Int,
                           execution_flags: collection.Map[String, Any] = collection.Map[String, Any](),
                           dagName: String)

final case class RouteDetails(path_prefixes: List[String],
                              health_check_path_prefixes: List[String],
                              http_verb: String,
                              default_dag: String)

final case class EdgeJSON(from: String,
                          to: String)

final case class NodeJSON(name: String,
                          class_path: String,
                          num_actor_instances: Int = 1,
                          startup_only_actor: Boolean = false,
                          execution_flags: collection.Map[String, Any] = collection.Map[String, Any]())

final case class DagJSON(name: String,
                         actors: List[NodeJSON],
                         edges: List[EdgeJSON],
                         required_fields: List[String],
                         terminate_request_on_failure_actors: Option[List[String]],
                         rename_fields: Option[Map[String, String]])

final case class DagSelectionValuesJson(dag_name: String,
                                        keys: Map[String, Any] = Map[String, Any]())

final case class DagSelectionKeysJson(selection_keys: Set[String] = Set[String](),
                                      values: List[DagSelectionValuesJson] = List[DagSelectionValuesJson]())

final case class ScalaGraphJSON(name: String,
                                nodes: (String, List[String]),
                                startup_only_nodes: Set[String],
                                terminate_request_on_failure_actors: Set[String],
                                edges: Map[String, List[Map[String, String]]],
                                requiredFields: List[String],
                                renameFields: Option[Map[String, String]]) {
  def this(dagJsonBlock: DagJSON) {
    this(dagJsonBlock.name,
      ("ActorNodes", dagJsonBlock.actors.map(e => e.name)),
      dagJsonBlock.actors.filter(e => e.startup_only_actor).map(e => e.name).toSet,
      dagJsonBlock.terminate_request_on_failure_actors.getOrElse(List[String]()).toSet,
      Map("DiEdge" -> dagJsonBlock.edges.map(e => Map("n1" -> e.from, "n2" -> e.to))),
      dagJsonBlock.required_fields, dagJsonBlock.rename_fields)
  }
}

final case class RouteConfig(actor_system_name: String,
                             host: String,
                             port: Int,
                             routes: List[RouteDetails],
                             actors: List[ActorSpec],
                             dags: List[ScalaGraphJSON],
                             dag_selection_keys: Option[DagSelectionKeysJson]) {
  /**
    * Constructor: RouteConfig object contains information loaded from the route file.
    *
    * @param actor_system_name String
    * @param host              String
    * @param port              Int
    * @param routes            List[RouteDetails]
    * @param dags              List[DagJson]
    */
  def this(actor_system_name: String,
           host: String,
           port: Int,
           routes: List[RouteDetails],
           dags: List[DagJSON],
           dag_selection_keys: Option[DagSelectionKeysJson] = None) = {
    this(actor_system_name,
      host,
      port,
      routes,
      dags.flatMap(d => d.actors.map(a => ActorSpec(a.name,
        a.class_path,
        a.num_actor_instances,
        a.execution_flags,
        d.name))),
      dags.map(dag => new ScalaGraphJSON(dag)),
      dag_selection_keys)
  }
}
