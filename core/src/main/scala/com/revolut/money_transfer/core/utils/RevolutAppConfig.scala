package com.revolut.money_transfer.core.utils

object RevolutAppConfig {

  private var _config: Option[Map[String, Any]] = _

  /**
    * Set the config map
    * @param config Map[String, Any]
    */
  def config_=(config: Map[String, Any]): Unit = _config = Some(config)

  /**
    * Get the config map
    * @return Option[Map[String, Any]]
    */
  def config: Option[Map[String, Any]] = _config

  /**
    * Clear the config
    * @return Unit
    */
  def clear(): Unit = _config = None

}
