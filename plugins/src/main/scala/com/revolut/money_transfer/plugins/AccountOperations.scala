package com.revolut.money_transfer.plugins

import java.util.UUID

import com.revolut.money_transfer.commons.StatusCodes
import com.revolut.money_transfer.plugins.decorators.Skippability
import com.revolut.money_transfer.plugins.utils.{ExceptionUtils, InputValidation, PluginUtils}
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods.parse

import scala.collection.mutable.{Map => MutableMap}
import scalaj.http.HttpStatusException

//Account Detail table schema
case class AccountDetail(
                         customerId:          String,
                         accountNumber:       String,
                         accountType:         String,
                         routingNumber:       String,
                         var accountBalance:  Double,
                         accountStatus:       String,
                         firstName:           String,
                         lastName:            String,
                         accountCreationDate: String,
                         accountClosureDate:  String
                       )

object AccountOperations extends Plugin with Skippability {

  implicit val formats = DefaultFormats

  val accountTable: MutableMap[String, Option[AccountDetail]] = MutableMap[String, Option[AccountDetail]]()
  var amount: Double = 0.0
  var withdrawAccountNo: String = ""

  override def startup(configMap: collection.Map[String, Any]): collection.Map[String, Any] = {
    logger.debug(s"${this.getClass.getSimpleName} -- on STARTUP received config: ${configMap.keySet}")
    logger.info(s"${this.getClass.getSimpleName} -- Loading all accounts data in memory .")

    InputValidation.validateMap(List("accountKey", "accountsFilePath"), configMap, Map(), this.getClass.getSimpleName)
    val accountsFilePath = configMap("accountsFilePath").asInstanceOf[String]
    //Load the file with all accounts data in memory
    loadRecords(configMap, PluginUtils.getStringAccounts(accountsFilePath))
    Map()
  }

  /**
    * Loads all the accounts from json file in memory
    * @param configMap
    * @param accounts
    */
  def loadRecords(configMap: collection.Map[String, Any], accounts: String):MutableMap[String, Option[AccountDetail]]= {
    val accountList = parse(accounts).extract[List[AccountDetail]]
    for ( account <- accountList) yield
      accountTable(account.accountNumber) = Option(account)
    accountTable
  }

  /**
    * Method called when processing a given API request.
    *
    * @param argsMap
    * @param configMap
    * @return map of execution results, which will be added to MessageCache
    */
  @throws[HttpStatusException]
  override def execute(argsMap: collection.Map[String, Any], configMap: collection.Map[String, Any]):
  collection.Map[String, Any] = {

    val accountKey = configMap("accountKey").asInstanceOf[String]
    val operation = configMap("actorName").asInstanceOf[String]
    val mutableArgs = MutableMap[String, Any]() ++ argsMap
    val transactionId = mutableArgs("transactionId").asInstanceOf[UUID].toString
    val logString = s"transactionId $transactionId -- ErrorMessage:: "
    var message: String = ""

    try{

      val accountNo = argsMap(accountKey).asInstanceOf[String]
      val account: Option[AccountDetail] = accountTable.getOrElse(accountNo, None)
      if ( !validateAccountId(accountNo) ) {
        message = s"$logString Account ID '${accountNo}' is not alphanumeric."
        throwBadRequestException(mutableArgs, message)
      }
      else if ( !account.isDefined ) {
        message = s"$logString Account ID '${accountNo}' not found."
        throwNotFoundException(mutableArgs, message)
      }

      amount = argsMap("body.transferAmount").asInstanceOf[Number].doubleValue
      withdrawAccountNo = argsMap("accountId").asInstanceOf[String]

      operation match {
        case "withdraw" =>
          if ( account.get.accountStatus == "Closed" || account.get.accountStatus == "Hold") {
            message = logString + "Withdrawal Account is in CLOSED or HOLD status. Please use an active Account Number"
            throwForbiddenRequestException(mutableArgs, message)
          }
          else if ( account.get.accountBalance - amount < 0.0 ) {
            message = logString + s"Insufficient Funds. Withdrawal amount of ${amount} is greater than Acct Balance."
            throwForbiddenRequestException(mutableArgs, message)
          }
          else if ( amount < 0.0 ) {
            message = logString + "Invalid request. Withdrawal amount cannot be negative."
            throwBadRequestException(mutableArgs, message)
          }
          else {
            account.get.accountBalance -= amount
            accountTable(accountNo) = account
            mutableArgs("withdrawFlag") = "true"
          }
        case "deposit" =>
          val withdrawalAccountNo = mutableArgs("accountId").asInstanceOf[String]
          if ( account.get.accountStatus == "Closed" || account.get.accountStatus == "Hold") {
            message = logString + "Deposit Account is in CLOSED or HOLD status. Please use an active Account Number."
            throwForbiddenRequestException(mutableArgs, message)
          }
          else if ( accountNo == withdrawalAccountNo &&
                    accountTable(accountNo).get.customerId == accountTable(withdrawalAccountNo).get.customerId ) {
            message = logString + "Deposit Account Number cannot be same as Withdrawal Account Number"
            throwBadRequestException(mutableArgs, message)
          }
          else {
            account.get.accountBalance += amount
            accountTable(accountNo) = account
            mutableArgs("depositAccountBalance") = account.get.accountBalance
            mutableArgs("withdrawalAccountBalance") = accountTable(withdrawalAccountNo).get.accountBalance
            mutableArgs("status") = "Success" //if deposit succeeds, mark status flag 'Success'
          }
        case _ =>
          message = logString + "Invalid operation. Please correct actor name in conf file."
          throwInternalServerException(mutableArgs, message)
      }
      mutableArgs
    } catch {
      case e @ (_:NoSuchElementException) =>
        throwBadRequestException(mutableArgs, e.toString)
        mutableArgs
      case e @ (_:ClassCastException) =>
        throwBadRequestException(mutableArgs, e.toString)
        mutableArgs
    }

  }

  /**
    * Given an accountId, this function validates that the account ID is alphanumeric or not
    * @param acccountId
    * @return Boolean
    */
  def validateAccountId(acccountId: String): Boolean = {
    val pattern = "^[a-zA-Z0-9]*$"
    acccountId.matches(pattern)
  }

  /**
    * Rolls back the amount to 'withdrawalAccount' if the withdraw operation succeeded but deposit failed
    * @param argsMap
    * @return
    */
  def rollBackAmount(argsMap: MutableMap[String, Any]): Map[String, Any] = {
    if ( argsMap.getOrElse("withdrawFlag", "false").asInstanceOf[String].toBoolean ) {
      accountTable(withdrawAccountNo).get.accountBalance += amount
      argsMap("withdrawalAccountBalance") = accountTable(withdrawAccountNo).get.accountBalance
    }
    argsMap("withdrawFlag") = "false"
    argsMap("status") = "Failed"
    argsMap.toMap
  }

  // $COVERAGE-OFF$
  def throwInternalServerException(argsMap: MutableMap[String, Any], message: String): Unit = {
    ExceptionUtils.throwException(rollBackAmount(argsMap),
      StatusCodes.InternalServerError, message, this.getClass.getSimpleName)
  }

  def throwNotFoundException(argsMap: MutableMap[String, Any], message: String): Unit = {
    ExceptionUtils.throwException(rollBackAmount(argsMap), StatusCodes.NotFound, message, this.getClass.getSimpleName)
  }

  def throwBadRequestException(argsMap: MutableMap[String, Any], message: String): Unit = {
    ExceptionUtils.throwException(rollBackAmount(argsMap), StatusCodes.BadRequest, message, this.getClass.getSimpleName)
  }

  def throwForbiddenRequestException(argsMap: MutableMap[String, Any], message: String): Unit = {
    ExceptionUtils.throwException(rollBackAmount(argsMap), StatusCodes.Forbidden, message, this.getClass.getSimpleName)
  }
}
