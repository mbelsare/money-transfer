package com.revolut.money_transfer.core.core.cache

import com.revolut.money_transfer.core.core.Dag

/*
 * Cache of DAG workflows
 * Populated at startup from JSON
 */
object DagCache extends Cache[String, Dag] {

  @inline
  def add(dag: Dag): Unit = super.add(dag.name, dag)

}
