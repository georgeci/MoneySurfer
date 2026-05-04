package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

class CalculateBalanceDeltaTest : StringSpec({

    val a = accountId("a-1")
    val b = accountId("a-2")
    val hundred = Money.fromMinor(100)
    val fifty = Money.fromMinor(50)

    "create income → +impact" {
        val new = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)
        calculateBalanceDelta(old = null, new = new) shouldBe mapOf(a to hundred)
    }

    "create expense → -impact" {
        val new = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        calculateBalanceDelta(old = null, new = new) shouldBe mapOf(a to -hundred)
    }

    "update amount expense 100 → 150 produces -50 delta" {
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        val new = old.copy(money = Money.fromMinor(150))
        calculateBalanceDelta(old, new) shouldBe mapOf(a to -fifty)
    }

    "move expense from A to B unwinds A and applies B" {
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        val new = old.copy(accountId = b)
        calculateBalanceDelta(old, new) shouldBe mapOf(a to hundred, b to -hundred)
    }

    "planned → actual applies full impact" {
        val old = aTransaction(
            accountId = a,
            money = hundred,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PLANNED,
        )
        val new = old.copy(status = TransactionStatus.ACTUAL)
        calculateBalanceDelta(old, new) shouldBe mapOf(a to -hundred)
    }

    "actual → planned unwinds full impact" {
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        val new = old.copy(status = TransactionStatus.PLANNED)
        calculateBalanceDelta(old, new) shouldBe mapOf(a to hundred)
    }

    "delete actual income unwinds impact" {
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)
        calculateBalanceDelta(old = old, new = null) shouldBe mapOf(a to -hundred)
    }

    "repeat delete (null/null) is empty delta" {
        calculateBalanceDelta(old = null, new = null).shouldBeEmpty()
    }

    "create planned produces empty delta" {
        val new = aTransaction(
            accountId = a,
            money = hundred,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PLANNED,
        )
        calculateBalanceDelta(old = null, new = new).shouldBeEmpty()
    }

    "no-op edit (same impact) yields empty delta" {
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        val new = old.copy(note = "edited note")
        calculateBalanceDelta(old, new).shouldBeEmpty()
    }
})
