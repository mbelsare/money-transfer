package com.revolut.money_transfer.core.utils

import java.io.File
import java.net.{URLClassLoader => ClassLoader}

object PluginLibJar {

  def load(jarFile: String): ClassLoader = {

    require(!jarFile.isEmpty)
    new ClassLoader(Array(new File(jarFile).toURI.toURL), getClass.getClassLoader)

  }
}
