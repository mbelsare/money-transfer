package com.revolut.money_transfer.plugins

import java.util.UUID

import com.revolut.money_transfer.plugins.decorators.Skippability
import org.json4s.DefaultFormats

//DbInsert plugin initializes connection with Database to store current transaction record
object DbInsert extends Plugin with Skippability {
  implicit val formats: DefaultFormats.type = org.json4s.DefaultFormats

  // $COVERAGE-OFF$

  /** Makes a connection to the DB specified in the configMap.
    *
    * @param configMap Map of config values read from Prop file
    * @return
    */
  override def startup(configMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    logger.debug(s"${this.getClass.getSimpleName} -- on STARTUP received config: ${configMap.keySet}")
    logger.info("Initialized connection to DB to store the current transaction.")

    configMap
  }

  // $COVERAGE-OFF$
  /**
    * Inserts fields from the argsMap into the DB
    *
    * @param argsMap   Map of values used to process each API request.
    *                  This Method appends argsMap with error if one occurs.
    * @param configMap Map of config values read from Prop file
    * @return Unit
    *
    */
  override def execute(argsMap: collection.Map[String, Any],
                       configMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    logger.debug(s"${this.getClass.getSimpleName} -- on EXECUTE received argsMap with keys ${argsMap.keySet}")
    logger.info(s"${this.getClass.getSimpleName} -- " +
      s"current transaction ${argsMap("transactionId").asInstanceOf[UUID].toString} written to database ..")

    argsMap
  }

  case class Column(columnName: String, columnType: String, isNullable: Boolean)

}
