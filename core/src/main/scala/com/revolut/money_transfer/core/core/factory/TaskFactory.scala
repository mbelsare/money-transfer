
package com.revolut.money_transfer.core.core.factory

import java.util.UUID

import com.revolut.money_transfer.core.core.cache.TaskCache
import com.revolut.money_transfer.core.core.{Dag, Task}

/*
 * Populates task cache for a given task workflow, called from Orchestrator
 */
object TaskFactory {

  def populateTasks(dag: Dag, tid: UUID): Unit = dag.nodes.foreach(node => TaskCache.add(new Task(tid, node)))

}
