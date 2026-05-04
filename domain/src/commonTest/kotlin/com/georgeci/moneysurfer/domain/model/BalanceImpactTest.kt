package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

class BalanceImpactTest : StringSpec({

    val account = accountId()
    val hundred = Money.fromMinor(100)

    "income contributes +amount on its account" {
        val tx = aTransaction(accountId = account, money = hundred, type = TransactionType.INCOME)
        tx.balanceImpact() shouldBe mapOf(account to hundred)
    }

    "expense contributes -amount on its account" {
        val tx = aTransaction(accountId = account, money = hundred, type = TransactionType.EXPENSE)
        tx.balanceImpact() shouldBe mapOf(account to -hundred)
    }

    "opening_balance contributes +amount on its account" {
        val tx = aTransaction(accountId = account, money = hundred, type = TransactionType.OPENING_BALANCE)
        tx.balanceImpact() shouldBe mapOf(account to hundred)
    }

    "planned transaction contributes nothing" {
        val tx = aTransaction(
            accountId = account,
            money = hundred,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PLANNED,
        )
        tx.balanceImpact().shouldBeEmpty()
    }

    "balanceImpact uses abs(money) so legacy signed money still produces correct sign" {
        val tx = aTransaction(
            accountId = account,
            money = -hundred,
            type = TransactionType.EXPENSE,
        )
        tx.balanceImpact() shouldBe mapOf(account to -hundred)
    }
})
