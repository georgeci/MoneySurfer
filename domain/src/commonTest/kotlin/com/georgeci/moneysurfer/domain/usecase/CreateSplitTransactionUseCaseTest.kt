package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * One receipt written as N sibling rows. The point of the shape is that each leg is an ordinary
 * transaction — so the balance moves by the whole payment and every category analytic sees a plain
 * row — while the shared id is what lets the list put them back together as one line.
 */
class CreateSplitTransactionUseCaseTest : StringSpec({

    val account = anAccount(id = accountId("a-1"), workspaceId = workspaceId(), currencyCode = USD)
    val operationDate = testDate
    val getCurrentTime = GetCurrentTimeUseCase(ClockUseCase(FixedClock(testInstant)))

    fun params(legs: List<CreateSplitTransactionUseCase.Leg>) = CreateSplitTransactionUseCase.Params(
        account = account,
        legs = legs,
        note = "Pyaterochka",
        operationAt = testInstant,
        operationDate = operationDate,
        type = TransactionType.EXPENSE,
    )

    val groceries = CreateSplitTransactionUseCase.Leg(categoryId("c-food"), Money.fromMinor(3_000))
    val chemicals = CreateSplitTransactionUseCase.Leg(categoryId("c-home"), Money.fromMinor(400))

    "the legs share one split id and differ only in category and amount" {
        val env = TransactionStoreEnv()
        val useCase = CreateSplitTransactionUseCase(env.applyChange, getCurrentTime)

        val written = useCase(params(listOf(groceries, chemicals)))

        written shouldHaveSize 2
        val splitId = written.first().splitId.shouldNotBeNull()
        written.map { it.splitId } shouldBe listOf(splitId, splitId)
        written.map { it.categoryId } shouldBe listOf(categoryId("c-food"), categoryId("c-home"))
        written.map { it.money } shouldBe listOf(Money.fromMinor(3_000), Money.fromMinor(400))
        // Everything that makes the group one payment is identical across the legs — the invariant
        // UpdateTransactionUseCase later defends when a single leg is edited.
        written.map { it.accountId }.distinct() shouldBe listOf(account.id)
        written.map { it.operationDate }.distinct() shouldBe listOf(operationDate)
        written.map { it.type }.distinct() shouldBe listOf(TransactionType.EXPENSE)
        written.map { it.currencyCode }.distinct() shouldBe listOf(USD)
    }

    "the legs are written through the change path, so the balance moves by the whole receipt" {
        val env = TransactionStoreEnv()
        val useCase = CreateSplitTransactionUseCase(env.applyChange, getCurrentTime)

        useCase(params(listOf(groceries, chemicals)))

        env.txStore.values shouldHaveSize 2
        env.balanceOf(account.id) shouldBe Money.fromMinor(-3_400)
    }

    // Both guards are programmer errors the creation screen already prevents; they exist so a new
    // caller cannot quietly write a group that no screen can render.
    "a single leg is not a split" {
        val env = TransactionStoreEnv()
        val useCase = CreateSplitTransactionUseCase(env.applyChange, getCurrentTime)

        shouldThrow<IllegalArgumentException> { useCase(params(listOf(groceries))) }
        env.txStore.values shouldHaveSize 0
    }

    "a leg carrying no money is refused" {
        val env = TransactionStoreEnv()
        val useCase = CreateSplitTransactionUseCase(env.applyChange, getCurrentTime)
        val empty = CreateSplitTransactionUseCase.Leg(categoryId("c-home"), Money.zero())

        shouldThrow<IllegalArgumentException> { useCase(params(listOf(groceries, empty))) }
        env.txStore.values shouldHaveSize 0
    }
})
