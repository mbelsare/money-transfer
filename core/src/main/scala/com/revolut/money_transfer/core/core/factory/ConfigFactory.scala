
package com.revolut.money_transfer.core.core.factory

import java.io.File
import java.util

import com.revolut.money_transfer.core.core.cache.ConfigCache
import com.revolut.money_transfer.core.utils.RevolutAppConfig
import com.revolut.money_transfer.core.case_classes.ActorSpec
import com.typesafe.config.{Config, ConfigFactory => TypesafeConfigFactory}

import scala.collection.JavaConverters._
import scala.collection.mutable

object ConfigFactory {

  /**
    * Takes in a configFile,
    * and populates RevolutAppConfig with the service map of the corresponding target environment.
    * Also populates the ConfigCache with each actor's config as specified in the given configFile,
    * or an empty map by default.
    * Subs in secrets from Chamber of Secrets vault into the config map if a "chamberOfSecrets" map
    * is specified in the configFile.
    *
    * @param configFile   File that contains the conf for a RevolutApp
    * @param actors       List of ActorSpec objects, each actor in the list will be populated in the ConfigCache
    *                     with its corresponding config from the configFile
    * @return typesafe config object containing the parsed contents of the original configFile
    */
  def populateConfig(configFile: File, actors: List[ActorSpec]): Config = {
    val config = TypesafeConfigFactory.load(TypesafeConfigFactory.parseFile(configFile))
    val targetEnv = config.getString("targetEnv").toLowerCase
    val configMap = config.getObject(targetEnv).unwrapped().asScala
    val service = configMap("service").asInstanceOf[util.HashMap[String, AnyRef]].asScala

    actors.foreach(actor => {
      ConfigCache +=
        (actor.name, mutable.Map[String, Any]("actorName" -> actor.name) ++
          service.getOrElse(actor.name, new util.HashMap[String, AnyRef]())
            .asInstanceOf[util.HashMap[String, AnyRef]].asScala)
    })
    RevolutAppConfig.config = Map[String, Any]() ++ service
    config
  }
}
