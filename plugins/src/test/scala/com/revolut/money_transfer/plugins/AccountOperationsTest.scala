package com.revolut.money_transfer.plugins

import java.util.UUID

import com.revolut.money_transfer.plugins.utils.PluginUtils
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, FlatSpec}

import scala.collection.mutable.{Map => MutableMap}

class AccountOperationsTest extends FlatSpec with BeforeAndAfterAll with MockitoSugar with BeforeAndAfter {

  val withdrawConfigMap: Map[String, Any] = Map(
    "accountKey" -> "accountId",
    "actorName" -> "withdraw")

  val depositConfigMap: Map[String, Any] = Map(
    "accountKey" -> "body.depositAccountId",
    "actorName" -> "deposit")

  val badConfigMap: Map[String, Any] = Map(
    "accountKey" -> "body.depositAccountId",
    "actorName" -> "deposit1")

  val validArgsMap = MutableMap[String, Any](
    "accountId" -> "1234",
    "body.depositAccountId" -> "1321",
    "body.transferAmount" -> 500.0,
    "transactionId" -> UUID.randomUUID()
  )

  val invalidArgsMap = MutableMap[String, Any](
    "accountId" -> "1289",
    "body.depositAccountId" -> "1289",
    "body.transferAmount" -> 300.0,
    "transactionId" -> UUID.randomUUID()
  )

  val invalidArgsMap2 = MutableMap[String, Any](
    "accountId" -> "1289#",
    "body.depositAccountId" -> "1221$",
    "body.transferAmount" -> 300.0,
    "transactionId" -> UUID.randomUUID()
  )

  val negativeAmountArgsMap = MutableMap[String, Any](
    "accountId" -> "1289",
    "body.depositAccountId" -> "1221",
    "body.transferAmount" -> -100.0,
    "transactionId" -> UUID.randomUUID()
  )

  val closedAccountArgsMap = MutableMap[String, Any](
    "accountId" -> "4321",
    "body.depositAccountId" -> "4321",
    "body.transferAmount" -> 650.5,
    "transactionId" -> UUID.randomUUID()
  )

  var accountTable: MutableMap[String, Option[AccountDetail]] = _

  before {
    accountTable = MutableMap[String, Option[AccountDetail]]()
    val accounts = PluginUtils.getStringAccounts(getClass.getClassLoader.getResource("test_accounts.json").getPath)
    accountTable = AccountOperations.loadRecords(withdrawConfigMap, accounts)
  }

  after{
    accountTable.clear()
  }

  behavior of "load file"
  it should "load the accounts in memory and create a map of accounts" in {
    assert(accountTable.isDefinedAt("1234"))
    assert(accountTable("1234").get.isInstanceOf[AccountDetail])
  }

  behavior of "validate account id"
  it should "return true for a valid numeric account id" in {
    assert(AccountOperations.validateAccountId("1234"))
  }

  it should "return true for a valid alphanumeric account id" in {
    assert(AccountOperations.validateAccountId("ab123"))
  }

  it should "return false for a invalid alphanumeric account id" in {
    assert(!AccountOperations.validateAccountId("ab123#"))
  }

  behavior of "startup"
  it should "load the account file in memory for a given valid path" in {
    val configMap = Map[String, Any]("accountKey" -> "accountId",
      "accountsFilePath" -> "plugins/src/test/resources/test_accounts.json")
    assert(AccountOperations.startup(configMap).isEmpty)
  }

  behavior of "execute: withdraw"
  it should "withdraw funds should return valid map when account has sufficient funds and accountId is valid" in {
    val executeMap = AccountOperations.execute(validArgsMap, withdrawConfigMap)
    assert(!executeMap.isEmpty)
    assert(accountTable("1234").get.accountBalance == 400.0)
  }

  it should "withdraw funds should throw Exception when withdrawal account has insufficient funds and " +
    "accountId is valid & active" in {
    assertThrows[Exception] {
      AccountOperations.execute(invalidArgsMap, withdrawConfigMap)
    }
  }

  it should "withdraw funds should throw Exception when account status is closed and accountId is valid" in {
    assertThrows[Exception] {
      AccountOperations.execute(closedAccountArgsMap, withdrawConfigMap)
    }
  }

  it should "withdraw funds should throw Exception when account has sufficient funds and accountId is valid " +
    "and withdrawal amount is negative" in {
    assertThrows[Exception] {
      AccountOperations.execute(negativeAmountArgsMap, withdrawConfigMap)
    }
  }

  it should "withdraw funds should throw Exception when account id is not alphanumeric" in {
    assertThrows[Exception] {
      AccountOperations.execute(invalidArgsMap2, withdrawConfigMap)
    }
  }

  it should "withdraw funds should throw Exception transfer amount is not double" in {
    val badTransferAmountMap = MutableMap[String, Any](
      "accountId" -> "1289",
      "body.depositAccountId" -> "1221",
      "body.transferAmount" -> "300.0",
      "transactionId" -> UUID.randomUUID()
    )
    assertThrows[Exception] {
      AccountOperations.execute(badTransferAmountMap, withdrawConfigMap)
    }
  }

  behavior of "execute: deposit"
  it should "deposit funds should return valid map when account account is in valid state" in {
    val executeMap = AccountOperations.execute(validArgsMap, depositConfigMap)
    assert(!executeMap.isEmpty)
    assert(executeMap("depositAccountBalance") == 4000.0)
  }

  it should "deposit funds should throw Exception when deposit account id is same as withdrawal account id & account " +
    "is in active state" in {
    assertThrows[Exception] {
      AccountOperations.execute(invalidArgsMap, depositConfigMap)
    }
  }

  it should "deposit funds should throw Exception when deposit account status is closed and accountId is valid" in {
    assertThrows[Exception] {
      AccountOperations.execute(closedAccountArgsMap, depositConfigMap)
    }
  }

  it should "deposit funds should throw Exception when account id is not alphanumeric" in {
    assertThrows[Exception] {
      AccountOperations.execute(invalidArgsMap2, depositConfigMap)
    }
  }

  it should "deposit funds should throw Exception and rollback withdrawal when deposit operation fails" in {
    val closedDepositAccountArgsMap = MutableMap[String, Any](
      "accountId" -> "1234",
      "withdrawFlag" -> "true",
      "body.depositAccountId" -> "4321",
      "body.transferAmount" -> 250.5,
      "transactionId" -> UUID.randomUUID()
    )

    try {
      AccountOperations.execute(closedDepositAccountArgsMap, depositConfigMap)
    } catch {
      case e @ (_:Exception) =>
        // account balance for accountId '1234' in test file is 900 but 'withdrawFlag' is true.
        assert(accountTable("1234").get.accountBalance == 1150.5)
    }
  }

  it should "throw Exception when account operation is not a valid operation" in {
    assertThrows[Exception] {
      AccountOperations.execute(validArgsMap, badConfigMap)
    }
  }

  behavior of "rollback method"
  it should "withdrawal accountId in accountTable should reflect original amount & mark 'withdraw' operation as false" +
    " if deposit operation fails" in {
    AccountOperations.withdrawAccountNo = "1234"
    AccountOperations.amount = 100.25
    val failedDepositOpArgsMap = MutableMap[String, Any](
      "accountId" -> "1234",
      "withdrawFlag" -> "true",
      "body.depositAccountId" -> "4321",
      "body.transferAmount" -> 100.25,
      "transactionId" -> UUID.randomUUID()
    )

    val rollbackMap = AccountOperations.rollBackAmount(failedDepositOpArgsMap)
    // 'withdrawFlag in 'failedDepositOpArgsMap' is true
    assert(accountTable("1234").get.accountBalance == 1000.25)
    assert(rollbackMap("withdrawFlag").asInstanceOf[String] == "false")
  }

}
