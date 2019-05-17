
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.core.{ROUTE_STATE, Route}

/*
 * Maps task ID to sender
 * Stores Route state (incomplete, finished successfully, finished unsuccessfully)
 * Populated by Orchestrator when beginning execution of a workflow
 */
object RouteCache extends Cache[UUID, Route] {

  @inline
  def add(route: Route): Unit =
    super.add(route.transactionId, route)

  @inline
  def getRouteState(id: UUID): ROUTE_STATE.Value = this(id).state

  @inline
  def updateRouteState(id: UUID, state: ROUTE_STATE.Value): Unit = this(id).state = state

}
