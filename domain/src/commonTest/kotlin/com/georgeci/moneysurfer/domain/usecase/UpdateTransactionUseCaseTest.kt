package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.splitId
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/**
 * The ordinary edit path, and the split invariant it has to protect.
 *
 * A split's legs are one receipt: they share account, currency, business date, moment and type, and
 * differ only in category and amount. The edit screen edits one leg and knows nothing about the
 * group, so the propagation lives here — without it, moving a receipt to the right date would move
 * a third of it and leave the rest behind, and the collapsed list row would stop describing one
 * payment.
 */
class UpdateTransactionUseCaseTest : StringSpec({

    val from = accountId("a-from")
    val other = accountId("a-other")
    val split = splitId("sp-1")
    val laterDate = LocalDate(2024, 3, 9)

    fun legs() = listOf(
        aTransaction(
            id = transactionId("leg-groceries"),
            accountId = from,
            money = Money.fromMinor(3_000),
            categoryId = categoryId("c-food"),
            splitId = split,
        ),
        aTransaction(
            id = transactionId("leg-chemicals"),
            accountId = from,
            money = Money.fromMinor(400),
            categoryId = categoryId("c-home"),
            splitId = split,
        ),
    )

    "editing one leg's date moves the whole receipt" {
        val env = TransactionStoreEnv()
        val useCase = UpdateTransactionUseCase(env.txRepo, env.applyChange)
        env.seed(*legs().toTypedArray())

        useCase(env.txStore.getValue(transactionId("leg-groceries")).copy(operationDate = laterDate))

        env.txStore.values.map { it.operationDate }.distinct() shouldBe listOf(laterDate)
    }

    "editing one leg's account moves the whole receipt, balances included" {
        val env = TransactionStoreEnv()
        val useCase = UpdateTransactionUseCase(env.txRepo, env.applyChange)
        env.seed(*legs().toTypedArray())

        useCase(env.txStore.getValue(transactionId("leg-groceries")).copy(accountId = other))

        env.txStore.values.map { it.accountId }.distinct() shouldBe listOf(other)
        // The sibling moves through the change path, so its money leaves the old account too —
        // otherwise the receipt would be charged to two accounts at once.
        env.balanceOf(from) shouldBe Money.fromMinor(3_400)
        env.balanceOf(other) shouldBe Money.fromMinor(-3_400)
    }

    "a leg's own category and amount stay its own" {
        val env = TransactionStoreEnv()
        val useCase = UpdateTransactionUseCase(env.txRepo, env.applyChange)
        env.seed(*legs().toTypedArray())

        useCase(
            env.txStore.getValue(transactionId("leg-groceries")).copy(
                categoryId = categoryId("c-snacks"),
                money = Money.fromMinor(2_500),
            ),
        )

        val sibling = env.txStore.getValue(transactionId("leg-chemicals"))
        sibling.categoryId shouldBe categoryId("c-home")
        sibling.money shouldBe Money.fromMinor(400)
        sibling.operationDate shouldBe testDate
    }

    "an ordinary transaction's edit touches nothing else" {
        val env = TransactionStoreEnv()
        val useCase = UpdateTransactionUseCase(env.txRepo, env.applyChange)
        val untouched = aTransaction(id = transactionId("t-2"), accountId = from)
        env.seed(aTransaction(id = transactionId("t-1"), accountId = from), untouched)

        useCase(env.txStore.getValue(transactionId("t-1")).copy(type = TransactionType.INCOME))

        env.txStore.getValue(transactionId("t-2")) shouldBe untouched
    }
})
