package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransferCounterpartUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first

/**
 * Multi-currency transfer creation (commit `6be038b`). The use case must:
 *   - emit two paired transactions sharing a `transferId` (EXPENSE on source,
 *     INCOME on destination), each tagged with its account's currency;
 *   - move balances by `fromMoney` / `toMoney` independently (cross-currency);
 *   - lazily seed the workspace's `Transfer` system category and reuse it.
 */
class TransferCreationIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
    }

    afterEach { harness.close() }

    "same-currency transfer creates two legs sharing transferId, EXPENSE on source and INCOME on destination" {
        val from = anAccount(
            id = accountId("a-from"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 1_000.dollars,
        )
        val to = anAccount(
            id = accountId("a-to"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 100.dollars,
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        stack.createTransfer(
            CreateTransferUseCase.Params(
                from = from,
                to = to,
                fromMoney = 200.dollars,
                toMoney = 200.dollars,
                note = "rent",
                operationAt = testInstant,
                operationDate = testDate,
            ),
        )

        val txns = stack.transactionRepository
            .getByWorkspaceId(DEFAULT_WORKSPACE_ID)
            .first()
            .sortedBy { it.type }

        txns.size shouldBe 2
        val expense = txns.single { it.type == TransactionType.EXPENSE }
        val income = txns.single { it.type == TransactionType.INCOME }

        expense.accountId shouldBe from.id
        income.accountId shouldBe to.id
        expense.transferId shouldNotBe null
        expense.transferId shouldBe income.transferId

        stack.accountRepository.getById(from.id)!!.balance shouldBe 800.dollars
        stack.accountRepository.getById(to.id)!!.balance shouldBe 300.dollars
    }

    "cross-currency transfer stores fromMoney/toMoney on respective legs with each account's currencyCode" {
        val from = anAccount(
            id = accountId("a-usd"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 500.dollars,
        )
        val to = anAccount(
            id = accountId("a-eur"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = EUR,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        stack.createTransfer(
            CreateTransferUseCase.Params(
                from = from,
                to = to,
                fromMoney = 100.dollars,
                toMoney = 92.dollars,
                note = "fx",
                operationAt = testInstant,
                operationDate = testDate,
            ),
        )

        val expense = stack.transactionRepository
            .getByAccountId(from.id).first().single()
        val income = stack.transactionRepository
            .getByAccountId(to.id).first().single()

        expense.type shouldBe TransactionType.EXPENSE
        expense.money shouldBe 100.dollars
        expense.currencyCode shouldBe USD

        income.type shouldBe TransactionType.INCOME
        income.money shouldBe 92.dollars
        income.currencyCode shouldBe EUR

        stack.accountRepository.getById(from.id)!!.balance shouldBe 400.dollars
        stack.accountRepository.getById(to.id)!!.balance shouldBe 92.dollars
    }

    "transfer auto-seeds Transfer category with systemKind=TRANSFER on first call and reuses it on subsequent calls" {
        val from = anAccount(
            id = accountId("a-from"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 1_000.dollars,
        )
        val to = anAccount(
            id = accountId("a-to"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        repeat(2) {
            stack.createTransfer(
                CreateTransferUseCase.Params(
                    from = from,
                    to = to,
                    fromMoney = 10.dollars,
                    toMoney = 10.dollars,
                    note = "n$it",
                    operationAt = testInstant,
                    operationDate = testDate,
                ),
            )
        }

        val transferCategories = stack.categoryRepository
            .getByWorkspaceId(DEFAULT_WORKSPACE_ID).first()
            .filter { it.systemKind == CategorySystemKind.TRANSFER }

        transferCategories.size shouldBe 1
        transferCategories.single().name shouldBe "Transfer"

        val txns = stack.transactionRepository
            .getByWorkspaceId(DEFAULT_WORKSPACE_ID).first()
        txns.size shouldBe 4
        txns.map { it.categoryId }.toSet() shouldBe setOf(transferCategories.single().id)
    }

    "getByTransferId returns only the legs of the requested transfer" {
        val from = anAccount(
            id = accountId("a-from"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 1_000.dollars,
        )
        val to = anAccount(
            id = accountId("a-to"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        // Two transfers plus a plain expense: the query has to pick one pair out of rows that
        // include another transfer and a row whose transferId is null.
        repeat(2) {
            stack.createTransfer(
                CreateTransferUseCase.Params(
                    from = from,
                    to = to,
                    fromMoney = 10.dollars,
                    toMoney = 10.dollars,
                    note = "n$it",
                    operationAt = testInstant,
                    operationDate = testDate,
                ),
            )
        }
        stack.transactionRepository.insert(
            aTransaction(
                id = transactionId("t-plain"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = from.id,
                money = 5.dollars,
                categoryId = null,
                type = TransactionType.EXPENSE,
            ),
        )

        val all = stack.transactionRepository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first()
        val targetTransferId = all.mapNotNull { it.transferId }.first()

        val legs = stack.transactionRepository.getByTransferId(targetTransferId)

        legs.size shouldBe 2
        legs.map { it.id }.toSet() shouldBe
            all.filter { it.transferId == targetTransferId }.map { it.id }.toSet()
        legs.map { it.type }.toSet() shouldBe setOf(TransactionType.EXPENSE, TransactionType.INCOME)
    }

    "the transfer counterpart resolves to the opposite leg through real storage" {
        val from = anAccount(
            id = accountId("a-from"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 1_000.dollars,
        )
        val to = anAccount(
            id = accountId("a-to"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(from)
        stack.accountRepository.insert(to)

        stack.createTransfer(
            CreateTransferUseCase.Params(
                from = from,
                to = to,
                fromMoney = 200.dollars,
                toMoney = 200.dollars,
                note = "rent",
                operationAt = testInstant,
                operationDate = testDate,
            ),
        )

        val getCounterpart = GetTransferCounterpartUseCase(stack.transactionRepository)
        val txns = stack.transactionRepository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first()
        val expense = txns.single { it.type == TransactionType.EXPENSE }
        val income = txns.single { it.type == TransactionType.INCOME }

        // This is what the details screen calls to say "Everyday → Savings"; each leg has to see
        // the other, never itself.
        getCounterpart(expense)?.id shouldBe income.id
        getCounterpart(income)?.id shouldBe expense.id

        val plain = aTransaction(
            id = transactionId("t-plain"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            accountId = from.id,
            money = 5.dollars,
            categoryId = null,
            type = TransactionType.EXPENSE,
        )
        stack.transactionRepository.insert(plain)
        getCounterpart(plain) shouldBe null
    }

    "transfer with from == to throws IllegalArgumentException" {
        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 100.dollars,
        )
        stack.accountRepository.insert(account)

        shouldThrow<IllegalArgumentException> {
            stack.createTransfer(
                CreateTransferUseCase.Params(
                    from = account,
                    to = account,
                    fromMoney = 10.dollars,
                    toMoney = 10.dollars,
                    note = "",
                    operationAt = testInstant,
                    operationDate = testDate,
                ),
            )
        }
    }
})
