
package com.revolut.money_transfer.core.core.cache

import scala.collection.concurrent.TrieMap

/*
 * Cache trait abstracts all common task functionality
 */
trait Cache[K, V] {

  private val _cache: TrieMap[K, V] = TrieMap[K, V]()

  @inline
  def add(kv: (K, V)): Unit = _cache.putIfAbsent(kv._1, kv._2)

  @inline
  def add(xv: TraversableOnce[(K, V)]): Unit = _cache ++= xv

  @inline
  def get(id: K): Option[V] = _cache.get(id)

  @inline
  def delete(key: K): Unit = _cache -= key

  @inline
  def delete(xv: TraversableOnce[K]): Unit = _cache --= xv

  @inline
  def contains(key: K): Boolean = _cache contains key

  @inline
  def clear(): Unit = _cache.clear()

  @inline
  def apply(key: K): V = _cache(key)

  @inline
  def +=(kv: (K, V)): Unit = add(kv)

  @inline
  def ++=(xv: TraversableOnce[(K, V)]): Unit = add(xv)

  @inline
  def -=(key: K): Unit = delete(key)

  @inline
  def --=(xv: TraversableOnce[K]): Unit = delete(xv)

  def isEmpty: Boolean = _cache.isEmpty

  @inline
  def getKeys: List[K] = _cache.keySet.toList

}
