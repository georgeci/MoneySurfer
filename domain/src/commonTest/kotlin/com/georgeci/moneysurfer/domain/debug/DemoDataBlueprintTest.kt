package com.georgeci.moneysurfer.domain.debug

import com.georgeci.moneysurfer.domain.constants.DEFAULT_CATEGORY_SEEDS
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val NOW = Instant.parse("2026-07-30T12:15:00Z")
private val UTC = TimeZone.UTC
private val WORKSPACE = WorkspaceId.uuid()
private val USD = CurrencyCode("USD")

private fun categories(): List<Category> = DEFAULT_CATEGORY_SEEDS.map { seed ->
    aCategory(workspaceId = WORKSPACE, name = seed.name, type = seed.type)
}

private fun snapshot(accounts: List<Account> = emptyList()) = DemoDataSnapshot(
    workspaceId = WORKSPACE,
    currency = USD,
    accounts = accounts,
    categories = categories(),
    budgetNames = emptySet(),
    goalTitles = emptySet(),
)

/** Amount + merchant + business date identifies a generated row without depending on its UUID. */
private fun List<Transaction>.fingerprints(): List<String> =
    map { "${it.money.minor}|${it.merchant}|${it.operationDate}" }

class DemoDataBlueprintTest : StringSpec({

    "the default seed follows the clock, so a second run is not a copy of the first" {
        // A constant seed made two runs on the same day replay each other row for row, which turns
        // "run it again to deepen the history" into 300 exact duplicates.
        val first = buildDemoDataPlan(snapshot(), NOW, UTC)
        val second = buildDemoDataPlan(snapshot(), NOW + 1.seconds, UTC)

        first.transactions.fingerprints() shouldNotBe second.transactions.fingerprints()
    }

    "an explicit seed still reproduces a plan exactly, which is what screenshot runs need" {
        val a = buildDemoDataPlan(snapshot(), NOW, UTC, seed = 42)
        val b = buildDemoDataPlan(snapshot(), NOW, UTC, seed = 42)

        a.transactions.fingerprints() shouldBe b.transactions.fingerprints()
    }

    "an archived account neither blocks its seed name nor collects any rows" {
        // Archived accounts are hidden from every list and excluded from total/budget rollups, so
        // rows filed against one are counted in the report and invisible in the app.
        val archivedCash = anAccount(
            id = AccountId.uuid(),
            workspaceId = WORKSPACE,
            name = "Cash",
            type = AccountType.CASH,
            currencyCode = USD,
            archived = true,
        )

        val plan = buildDemoDataPlan(snapshot(accounts = listOf(archivedCash)), NOW, UTC)

        plan.accounts.map { it.name } shouldContain "Cash"
        plan.accounts.none { it.archived } shouldBe true
        plan.transactions.map { it.accountId } shouldNotContain archivedCash.id
    }

    "an active account of the same name is reused rather than duplicated" {
        val cash = anAccount(
            id = AccountId.uuid(),
            workspaceId = WORKSPACE,
            name = "Cash",
            type = AccountType.CASH,
            currencyCode = USD,
        )

        val plan = buildDemoDataPlan(snapshot(accounts = listOf(cash)), NOW, UTC)

        plan.accounts.map { it.name } shouldBe listOf("Checking", "Savings")
        // No opening balance for a row this run did not create — it already has its own history.
        plan.transactions.count { it.type == TransactionType.OPENING_BALANCE } shouldBe 2
    }
})
