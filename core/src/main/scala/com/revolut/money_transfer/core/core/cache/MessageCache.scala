
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import scala.collection.concurrent.TrieMap

/*
 * Cache for messages that are passed between capabilities
 */
object MessageCache extends Cache[UUID, collection.Map[String, Any]] {

  @inline
  def add(id: UUID, argsMap: collection.Map[String, Any]): Unit = {
    super.add(id, TrieMap[String, Any]() ++ argsMap)
  }

  @inline
  override def get(id: UUID): Option[collection.Map[String, Any]] = {
    val res = super.get(id)
    if (res.isDefined) Some(res.get.toMap[String, Any]) else None
  }

  @inline
  override def apply(id: UUID): collection.Map[String, Any] = {
    super.apply(id).toMap[String, Any]
  }

  @inline
  def append(id: UUID, argsMap: collection.Map[String, Any]): Unit = {
    super.apply(id).asInstanceOf[TrieMap[String, Any]] ++= (argsMap -- super.apply(id).keySet)
  }

  @inline
  def appendIfDefined(id: UUID, argsMap: collection.Map[String, Any]): Unit = {
    if (get(id).isDefined) {
      append(id, argsMap)
    }
  }

}
