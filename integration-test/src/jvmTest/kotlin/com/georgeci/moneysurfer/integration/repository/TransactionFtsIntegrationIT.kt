package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.FtsQuery
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * Exercises the `transactions_fts` virtual table against the real bundled SQLite:
 *   - the external-content triggers Room generates keep the index in sync on
 *     insert / update / delete without any manual maintenance,
 *   - both indexed columns (`note` and `merchant`) are reachable from one MATCH,
 *   - the `unicode61` tokenizer actually splits Cyrillic (the whole reason the
 *     default `simple` tokenizer is not used),
 *   - [FtsQuery] output is valid MATCH syntax for adversarial user input.
 */
class TransactionFtsIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness

    suspend fun search(raw: String): List<String> {
        val query = FtsQuery.fromUserInput(raw) ?: return emptyList()
        return harness.database.transactionDao()
            .searchByText(workspaceId = "ws-1", query = query)
            .first()
            .map { it.id }
    }

    beforeEach {
        harness = IntegrationHarness()
        harness.database.userDao().insert(
            UserEntity(id = "owner-uid", displayName = "Owner", isAnon = false),
        )
        harness.database.workspaceDao().insert(
            WorkspaceEntity(
                id = "ws-1",
                name = "WS",
                description = "",
                baseCurrency = "USD",
                ownerId = "owner-uid",
                createdAt = NOW,
                archived = false,
                updatedAt = NOW,
            ),
        )
        harness.database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                workspaceId = "ws-1",
                name = "Cash",
                type = "CASH",
                currency = "USD",
                balance = 0L,
            ),
        )
    }

    afterEach { harness.close() }

    "matches a whole word in the middle of a note" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "Coffee at the airport"),
                transaction(id = "t-2", note = "Train ticket"),
            ),
        )

        search("airport") shouldBe listOf("t-1")
    }

    "matches a Cyrillic prefix — unicode61 tokenizer, not simple" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "Продукты в Пятёрочке"),
                transaction(id = "t-2", note = "Бензин"),
            ),
        )

        search("прод") shouldBe listOf("t-1")
        search("Пятёроч") shouldBe listOf("t-1")
    }

    "AND-s multiple tokens" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "taxi to airport"),
                transaction(id = "t-2", note = "taxi home"),
            ),
        )

        search("taxi airport") shouldBe listOf("t-1")
        search("taxi").sorted() shouldBe listOf("t-1", "t-2")
    }

    "results keep the newest-first ordering of the list queries" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-old", note = "rent", operationDate = "2026-01-01"),
                transaction(id = "t-new", note = "rent", operationDate = "2026-03-01"),
                transaction(id = "t-mid", note = "rent", operationDate = "2026-02-01"),
            ),
        )

        search("rent") shouldBe listOf("t-new", "t-mid", "t-old")
    }

    "index follows the content table on update and delete" {
        val dao = harness.database.transactionDao()
        dao.insert(transaction(id = "t-1", note = "groceries"))

        dao.update(transaction(id = "t-1", note = "pharmacy"))
        search("groceries").shouldBeEmpty()
        search("pharmacy") shouldBe listOf("t-1")

        dao.delete("t-1")
        search("pharmacy").shouldBeEmpty()
    }

    "does not leak rows from another workspace" {
        harness.database.workspaceDao().insert(
            WorkspaceEntity(
                id = "ws-2",
                name = "Other",
                description = "",
                baseCurrency = "USD",
                ownerId = "owner-uid",
                createdAt = NOW,
                archived = false,
                updatedAt = NOW,
            ),
        )
        harness.database.accountDao().insert(
            AccountEntity(
                id = "acc-2",
                workspaceId = "ws-2",
                name = "Cash",
                type = "CASH",
                currency = "USD",
                balance = 0L,
            ),
        )
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "shared word"),
                transaction(id = "t-2", note = "shared word", workspaceId = "ws-2", accountId = "acc-2"),
            ),
        )

        search("shared") shouldBe listOf("t-1")
    }

    "matches the merchant column, not just the note" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "morning fix", merchant = "Starbucks"),
                transaction(id = "t-2", note = "lunch", merchant = "Pret"),
            ),
        )

        search("starbucks") shouldBe listOf("t-1")
        // One MATCH spans both indexed columns, so a query may straddle them.
        search("starbucks morning") shouldBe listOf("t-1")
    }

    "merchant edits follow the content table like note edits do" {
        val dao = harness.database.transactionDao()
        dao.insert(transaction(id = "t-1", note = "coffee", merchant = "Starbucks"))

        dao.update(transaction(id = "t-1", note = "coffee", merchant = "Pret"))

        search("starbucks").shouldBeEmpty()
        search("pret") shouldBe listOf("t-1")
    }

    "operator keywords and quotes from user input do not break MATCH" {
        harness.database.transactionDao().insertAll(
            listOf(
                transaction(id = "t-1", note = "OR NOT to be"),
                transaction(id = "t-2", note = "something else"),
            ),
        )

        // Bare OR/NOT would be parsed as FTS operators; quotes would be a syntax error.
        search("OR NOT") shouldBe listOf("t-1")
        search("\"unbalanced").shouldBeEmpty()
        search("   ").shouldBeEmpty()
        search("!!!").shouldBeEmpty()
    }
})

private const val NOW: Long = 1_700_000_000_000L

private fun transaction(
    id: String,
    note: String,
    merchant: String = "",
    workspaceId: String = "ws-1",
    accountId: String = "acc-1",
    operationDate: String = "2026-02-01",
) = TransactionEntity(
    id = id,
    workspaceId = workspaceId,
    accountId = accountId,
    amount = 100L,
    currencyCode = "USD",
    categoryId = null,
    note = note,
    merchant = merchant,
    operationAt = NOW,
    operationDate = operationDate,
    type = "EXPENSE",
    createdAt = NOW,
    updatedAt = NOW,
)
