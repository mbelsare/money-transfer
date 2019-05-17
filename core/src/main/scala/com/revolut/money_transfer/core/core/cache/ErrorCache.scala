
package com.revolut.money_transfer.core.core.cache

import java.util.UUID

import com.revolut.money_transfer.core.case_classes.ErrorMessage

import scala.collection.mutable.MutableList

object ErrorCache extends Cache[UUID, Seq[ErrorMessage]] {

  @inline
  def add(id: UUID, errorMessage: ErrorMessage): Unit = super.add(id, MutableList(errorMessage))

  @inline
  def init(id: UUID): Unit = super.add(id, MutableList())

  @inline
  override def get(id: UUID): Option[Seq[ErrorMessage]] = {
    val res = super.get(id)
    if (res.isDefined) Some(List() ++ res.get) else None
  }

  @inline
  override def apply(id: UUID): Seq[ErrorMessage] =
    List() ++ super.apply(id)

  @inline
  def append(id: UUID, errorMessage: ErrorMessage): Unit =
    super.apply(id).asInstanceOf[MutableList[ErrorMessage]] += errorMessage

  @inline
  def appendIfDefined(id: UUID, errorMessage: ErrorMessage): Unit =
    if (get(id).isDefined) {
      append(id, errorMessage)
    }
}
