
package com.revolut.money_transfer.core.core.cache

import scala.collection.concurrent.TrieMap

/*
 * Stores configuration information for db, xds, etc.
 * Populated at startup from application config
 */
object ConfigCache extends Cache[String, collection.Map[String, Any]] {

  @inline
  def add(actorName: String, config: collection.Map[String, Any]): Unit =
    super.add(actorName, TrieMap[String, Any]() ++= config)

  @inline
  override def get(kv: String): Option[collection.Map[String, Any]] = {
    val res = super.get(kv)
    if (res.isDefined) Some(res.get.toMap[String, Any]) else None
  }

  @inline
  override def apply(actorName: String): collection.Map[String, Any] =
    super.apply(actorName).toMap[String, Any]

  @inline
  def append(actorName: String, config: collection.Map[String, Any]): Unit =
    super.apply(actorName).asInstanceOf[TrieMap[String, Any]] ++= config

  @inline
  def +=(actorName: String, config: collection.Map[String, Any]): Unit =
    add(actorName, config)

}
