package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ApplyTransactionChangeUseCaseTest : StringSpec({

    val a = accountId("a-1")
    val b = accountId("a-2")
    val hundred = Money.fromMinor(100)

    "create income increments cache by +amount" {
        val env = TransactionStoreEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe hundred
        env.txStore[tx.id] shouldBe tx
    }

    "create expense decrements cache by amount" {
        val env = TransactionStoreEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe -hundred
    }

    "update amount applies only the difference" {
        val env = TransactionStoreEnv()
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = old)
        val new = old.copy(money = Money.fromMinor(150))

        env.useCase(old = old, new = new)

        env.balanceOf(a) shouldBe Money.fromMinor(-150)
    }

    "moving expense between accounts unwinds old and applies new" {
        val env = TransactionStoreEnv()
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = old)
        val new = old.copy(accountId = b)

        env.useCase(old = old, new = new)

        env.balanceOf(a) shouldBe Money.zero()
        env.balanceOf(b) shouldBe -hundred
    }

    "planned → actual applies impact" {
        val env = TransactionStoreEnv()
        val planned = aTransaction(
            accountId = a,
            money = hundred,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PLANNED,
        )
        env.useCase(old = null, new = planned)
        env.balanceOf(a) shouldBe Money.zero()

        val actual = planned.copy(status = TransactionStatus.ACTUAL)
        env.useCase(old = planned, new = actual)

        env.balanceOf(a) shouldBe -hundred
    }

    "actual → planned unwinds impact" {
        val env = TransactionStoreEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = tx)
        env.balanceOf(a) shouldBe -hundred

        env.useCase(old = tx, new = tx.copy(status = TransactionStatus.PLANNED))

        env.balanceOf(a) shouldBe Money.zero()
    }

    "delete unwinds impact and removes the row" {
        val env = TransactionStoreEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)
        env.useCase(old = null, new = tx)

        env.useCase(old = tx, new = null)

        env.balanceOf(a) shouldBe Money.zero()
        env.txStore.containsKey(tx.id) shouldBe false
    }

    "repeat delete (null/null) is a no-op" {
        val env = TransactionStoreEnv()
        env.useCase(old = null, new = null)
        env.balanceOf(a) shouldBe Money.zero()
    }

    "opening_balance behaves as a regular ACTUAL for cache" {
        val env = TransactionStoreEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.OPENING_BALANCE)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe hundred
    }
})
