package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first

/**
 * Drives `CreateTransactionUseCase` end-to-end: the use case must persist the
 * transaction row AND apply the correct signed balance delta to the account
 * (via `ApplyTransactionChangeUseCase`). Catches regressions where transaction
 * type → balance sign mapping or currencyCode column drift.
 */
class TransactionCreationIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
        stack.categoryRepository.insert(
            aCategory(id = categoryId("c-1"), workspaceId = DEFAULT_WORKSPACE_ID),
        )
    }

    afterEach { harness.close() }

    "EXPENSE creation persists row and decreases account balance by amount" {
        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 500.dollars,
        )
        stack.accountRepository.insert(account)

        stack.createTransaction(
            aTransaction(
                id = transactionId("t-exp"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account.id,
                money = 80.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                type = TransactionType.EXPENSE,
            ),
        )

        val stored = stack.transactionRepository.getById(transactionId("t-exp"))
        stored shouldNotBe null
        stored!!.type shouldBe TransactionType.EXPENSE
        stored.money shouldBe 80.dollars
        stored.currencyCode shouldBe USD

        stack.accountRepository.getById(account.id)!!.balance shouldBe 420.dollars
    }

    "INCOME creation persists row and increases account balance by amount" {
        val account = anAccount(
            id = accountId("a-1"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(account)

        stack.createTransaction(
            aTransaction(
                id = transactionId("t-inc"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account.id,
                money = 200.dollars,
                currencyCode = USD,
                categoryId = categoryId("c-1"),
                type = TransactionType.INCOME,
            ),
        )

        val stored = stack.transactionRepository.getById(transactionId("t-inc"))!!
        stored.type shouldBe TransactionType.INCOME

        stack.accountRepository.getById(account.id)!!.balance shouldBe 200.dollars
    }

    "transaction stores its own currencyCode independent of the account's currency" {
        val account = anAccount(
            id = accountId("a-usd"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 100.dollars,
        )
        stack.accountRepository.insert(account)

        stack.createTransaction(
            aTransaction(
                id = transactionId("t-eur"),
                workspaceId = DEFAULT_WORKSPACE_ID,
                accountId = account.id,
                money = 30.dollars,
                currencyCode = EUR,
                categoryId = categoryId("c-1"),
                type = TransactionType.EXPENSE,
            ),
        )

        val stored = stack.transactionRepository.getById(transactionId("t-eur"))!!
        stored.currencyCode shouldBe EUR

        stack.transactionRepository.getByAccountId(account.id).first().single()
            .currencyCode shouldBe EUR
    }
})
