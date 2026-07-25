package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.first

/**
 * Account creation through `AccountRepositoryImpl` against real Room. Guards the
 * currency-selector flow added in `8156d84`: a freshly created account must
 * round-trip its `CurrencyCode` and balance untouched, and `applyDelta` must
 * move balance without touching the currency column.
 */
class AccountCreationIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var stack: FinanceStack

    beforeEach {
        harness = IntegrationHarness()
        stack = FinanceStack(harness)
        harness.seedWorkspace()
    }

    afterEach { harness.close() }

    "inserts account with USD and round-trips currency + balance" {
        val account = anAccount(
            id = accountId("a-usd"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = USD,
            balance = 250.dollars,
        )

        stack.accountRepository.insert(account)

        val stored = stack.accountRepository.getById(account.id)
        stored shouldNotBe null
        stored!!.currencyCode shouldBe USD
        stored.balance shouldBe 250.dollars
        stored.workspaceId shouldBe DEFAULT_WORKSPACE_ID
    }

    "inserts accounts with USD, EUR, RUB in same workspace and getByWorkspaceId returns all three" {
        stack.accountRepository.insert(
            anAccount(id = accountId("a-usd"), workspaceId = DEFAULT_WORKSPACE_ID, currencyCode = USD),
        )
        stack.accountRepository.insert(
            anAccount(id = accountId("a-eur"), workspaceId = DEFAULT_WORKSPACE_ID, currencyCode = EUR),
        )
        stack.accountRepository.insert(
            anAccount(id = accountId("a-rub"), workspaceId = DEFAULT_WORKSPACE_ID, currencyCode = RUB),
        )

        val rows = stack.accountRepository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first()
        // Sorted by id here rather than relying on the DAO's own `ORDER BY sortOrder, name, id`:
        // this test is about the three currencies surviving the round trip, and the order they
        // come back in is pinned by AccountSortOrderIntegrationIT.
        rows.map { it.id.value to it.currencyCode }.sortedBy { it.first } shouldBe listOf(
            "a-eur" to EUR,
            "a-rub" to RUB,
            "a-usd" to USD,
        )
    }

    "applyDelta updates balance without touching currency" {
        val account = anAccount(
            id = accountId("a-eur"),
            workspaceId = DEFAULT_WORKSPACE_ID,
            currencyCode = EUR,
            balance = Money.zero(),
        )
        stack.accountRepository.insert(account)

        stack.accountRepository.applyDelta(account.id, 75.dollars)

        val stored = stack.accountRepository.getById(account.id)!!
        stored.balance shouldBe 75.dollars
        stored.currencyCode shouldBe EUR
    }

    "archiving stamps archivedAt and restoring clears it" {
        val account = anAccount(id = accountId("a-eur"), workspaceId = DEFAULT_WORKSPACE_ID)
        stack.accountRepository.insert(account)
        stack.accountRepository.getById(account.id)!!.archivedAt shouldBe null

        stack.accountRepository.setArchived(account.id, archived = true)

        val archived = stack.accountRepository.getById(account.id)!!
        archived.archived shouldBe true
        archived.archivedAt shouldNotBe null

        stack.accountRepository.setArchived(account.id, archived = false)

        val restored = stack.accountRepository.getById(account.id)!!
        restored.archived shouldBe false
        restored.archivedAt shouldBe null
    }
})
