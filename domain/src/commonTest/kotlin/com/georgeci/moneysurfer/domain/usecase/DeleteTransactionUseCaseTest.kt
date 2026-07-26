package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.transferId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * What a delete takes with it, and what it hands back for the Undo. The rows it returns are the
 * Undo's only input — see [RestoreTransactionsUseCase] — so anything it forgets to return is data
 * the user can never get back.
 */
class DeleteTransactionUseCaseTest : StringSpec({

    val from = accountId("a-from")
    val to = accountId("a-to")
    val hundred = Money.fromMinor(100)

    "an ordinary row is removed on its own and returned for the undo" {
        val env = TransactionStoreEnv()
        val useCase = DeleteTransactionUseCase(env.txRepo, env.applyChange)
        val other = aTransaction(id = transactionId("t-2"), accountId = from, money = hundred)
        env.seed(aTransaction(id = transactionId("t-1"), accountId = from, money = hundred), other)

        val deleted = useCase(transactionId("t-1"))

        deleted.map { it.id } shouldBe listOf(transactionId("t-1"))
        env.txStore.keys shouldBe setOf(transactionId("t-2"))
    }

    "deleting one leg of a transfer takes the other with it" {
        val env = TransactionStoreEnv()
        val useCase = DeleteTransactionUseCase(env.txRepo, env.applyChange)
        val transfer = transferId("tr-1")
        env.seed(
            aTransaction(
                id = transactionId("leg-out"),
                accountId = from,
                money = hundred,
                type = TransactionType.EXPENSE,
                transferId = transfer,
            ),
            aTransaction(
                id = transactionId("leg-in"),
                accountId = to,
                money = hundred,
                type = TransactionType.INCOME,
                transferId = transfer,
            ),
            aTransaction(id = transactionId("unrelated"), accountId = from, money = hundred),
        )

        // Swiping either leg is the same act; this is the one the user did not open.
        val deleted = useCase(transactionId("leg-in"))

        deleted.map { it.id }.toSet() shouldBe setOf(transactionId("leg-out"), transactionId("leg-in"))
        env.txStore.keys shouldBe setOf(transactionId("unrelated"))
        // Both sides unwound: the receiving account does not keep money that no longer came from
        // anywhere.
        env.balanceOf(from) shouldBe hundred
        env.balanceOf(to) shouldBe -hundred
    }

    // The pair may already be broken by an old import or a half-synced device. Deleting what the
    // user pointed at still has to work; there is simply nothing else to take.
    "a leg whose sibling is already gone deletes on its own" {
        val env = TransactionStoreEnv()
        val useCase = DeleteTransactionUseCase(env.txRepo, env.applyChange)
        env.seed(
            aTransaction(id = transactionId("t-1"), accountId = from, money = hundred)
                .copy(transferId = transferId("tr-lonely")),
        )

        val deleted = useCase(transactionId("t-1"))

        deleted.map { it.id } shouldBe listOf(transactionId("t-1"))
        env.txStore.keys.shouldBeEmpty()
    }

    "deleting an id that is not there returns nothing, so no Undo is offered" {
        val env = TransactionStoreEnv()
        val useCase = DeleteTransactionUseCase(env.txRepo, env.applyChange)

        useCase(transactionId("ghost")).shouldBeEmpty()
    }
})
