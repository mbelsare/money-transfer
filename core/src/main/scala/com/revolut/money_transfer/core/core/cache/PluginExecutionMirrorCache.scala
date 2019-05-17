
package com.revolut.money_transfer.core.core.cache

import scala.reflect.runtime.universe.{InstanceMirror, MethodSymbol, Mirror, ModuleMirror, ModuleSymbol}

object PluginExecutionMirrorCache extends Cache[String, Map[String, Any]] {

  @inline
  def add(name: String, mirrorMap: Map[String, Any]): Unit = super.add(name, mirrorMap)

  @inline
  def getModule(name: String): Option[ModuleSymbol] = Some(super.get(name)
    .get("module")
    .asInstanceOf[ModuleSymbol])

  @inline
  def getModuleMirror(name: String): Option[ModuleMirror] = Some(super.get(name)
    .get("moduleMirror")
    .asInstanceOf[ModuleMirror])

  @inline
  def getInstanceMirror(name: String): Option[InstanceMirror] = Some(super.get(name)
    .get("instanceMirror")
    .asInstanceOf[InstanceMirror])

  @inline
  def getMirror(name: String): Option[Mirror] = Some(super.get(name)
    .get("mirror")
    .asInstanceOf[Mirror])

  def getStartupMethod(name: String): Option[MethodSymbol] = Some(super.get(name)
    .get("methodStartup")
    .asInstanceOf[MethodSymbol])

  def getExecuteMethod(name: String): Option[MethodSymbol] = Some(super.get(name)
    .get("methodExecute")
    .asInstanceOf[MethodSymbol])

  def getShutdownMethod(name: String): Option[MethodSymbol] = Some(super.get(name)
    .get("methodShutdown")
    .asInstanceOf[MethodSymbol])

  @inline
  override def getKeys(): List[String] = super.getKeys
}
