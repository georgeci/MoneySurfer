package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * The three `config_entry` write statements against the real schema.
 *
 * They exist as hand-written SQL rather than `@Upsert` precisely because each has to leave a
 * different column alone, and that is the part a fake DAO cannot prove.
 */
class ConfigEntryDaoIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var dao: ConfigEntryDao

    beforeEach {
        harness = IntegrationHarness()
        dao = harness.database.configEntryDao()
    }

    afterEach {
        harness.close()
    }

    "a local write inserts, then updates value and updatedAt in place" {
        dao.write(key = THEME, value = "Dark", updatedAt = 100)
        dao.write(key = THEME, value = "Light", updatedAt = 200)

        val row = dao.getByKey(THEME)
        row?.value shouldBe "Light"
        row?.updatedAt shouldBe 200
        dao.getAll().map { it.key } shouldContainExactly listOf(THEME)
    }

    "a local write leaves lastPushedAt alone, so the row reads as pending again" {
        // The column is missing from the statement's update list on purpose: a value written after
        // a push must be pushed again, and this is the only record that it was not.
        dao.write(key = THEME, value = "Dark", updatedAt = 100)
        dao.markPushed(key = THEME, pushedUpdatedAt = 100)
        dao.keysPendingPush().shouldBeEmpty()

        dao.write(key = THEME, value = "Light", updatedAt = 200)

        dao.getByKey(THEME)?.lastPushedAt shouldBe 100
        dao.keysPendingPush() shouldContainExactly listOf(THEME)
    }

    "a pulled value is stamped as already pushed" {
        // Otherwise the sign-in reconciliation would push the server's own value straight back.
        dao.applyRemote(key = THEME, value = "Dark", updatedAt = 300)

        dao.getByKey(THEME)?.lastPushedAt shouldBe 300
        dao.keysPendingPush().shouldBeEmpty()
    }

    "a pull overwrites a local row and clears its pending state" {
        dao.write(key = THEME, value = "Light", updatedAt = 100)

        dao.applyRemote(key = THEME, value = "Dark", updatedAt = 300)

        dao.getByKey(THEME)?.value shouldBe "Dark"
        dao.keysPendingPush().shouldBeEmpty()
    }

    "markPushed ignores a row that changed after the push read it" {
        // The push sent the value stamped 100; by the time it returned the user had written 200.
        // Marking that row pushed would strand the newer value on this device forever.
        dao.write(key = THEME, value = "Dark", updatedAt = 100)
        dao.write(key = THEME, value = "Light", updatedAt = 200)

        dao.markPushed(key = THEME, pushedUpdatedAt = 100)

        dao.getByKey(THEME)?.lastPushedAt.shouldBeNull()
        dao.keysPendingPush() shouldContainExactly listOf(THEME)
    }

    "a never-pushed row is pending" {
        dao.write(key = THEME, value = "Dark", updatedAt = 100)
        dao.write(key = PERIOD, value = "Month", updatedAt = 100)
        dao.markPushed(key = PERIOD, pushedUpdatedAt = 100)

        dao.keysPendingPush() shouldContainExactly listOf(THEME)
    }

    "observeAll re-emits after every write, including one made by the pull" {
        // This is what carries a setting changed on another device into the in-memory mirror, and
        // from there into the running UI.
        dao.observeAll().first().shouldBeEmpty()

        dao.applyRemote(key = THEME, value = "Dark", updatedAt = 300)

        dao.observeAll().first().map { it.value } shouldContainExactly listOf("Dark")
    }

    "deleteAll empties the table — the account-scoped wipe" {
        dao.write(key = THEME, value = "Dark", updatedAt = 100)
        dao.write(key = PERIOD, value = "Month", updatedAt = 100)

        dao.deleteAll()

        dao.getAll().shouldBeEmpty()
    }
})

private const val THEME = "ui.theme_mode"
private const val PERIOD = "ui.transactions_period_mode"
