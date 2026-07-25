package com.georgeci.moneysurfer.integration.repository

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_32_33
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The v33 backfill against a real SQLite. It is a correlated subquery that counts each row's
 * predecessors *within its own workspace* — the part worth pinning is that it neither leaks
 * across workspaces nor resolves the outer table to the inner alias.
 */
class AccountSortOrderMigrationIT : StringSpec({

    "the backfill numbers each workspace's accounts by insertion order, independently" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV32Accounts()
            // Inserted deliberately out of alphabetical order: if the backfill were skipped, the
            // new `ORDER BY sortOrder, name` would re-sort these and the upgrade would be visible.
            connection.insertAccount(id = "w1-first", workspaceId = "ws-1", name = "Zebra")
            connection.insertAccount(id = "w2-first", workspaceId = "ws-2", name = "Yak")
            connection.insertAccount(id = "w1-second", workspaceId = "ws-1", name = "Aardvark")
            connection.insertAccount(id = "w1-third", workspaceId = "ws-1", name = "Manatee")
            connection.insertAccount(id = "w2-second", workspaceId = "ws-2", name = "Bison")

            MIGRATION_32_33.migrate(connection)

            connection.readSortOrders() shouldBe listOf(
                "w1-first" to 0,
                "w1-second" to 1,
                "w1-third" to 2,
                // Numbering restarts per workspace rather than continuing from ws-1.
                "w2-first" to 0,
                "w2-second" to 1,
            )
        } finally {
            connection.close()
        }
    }

    "an empty accounts table survives the backfill" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV32Accounts()

            MIGRATION_32_33.migrate(connection)

            connection.readSortOrders() shouldBe emptyList()
        } finally {
            connection.close()
        }
    }
})

/** The columns of `accounts` the migration touches, as they stand at v32. */
private fun SQLiteConnection.createV32Accounts() = execSQL(
    "CREATE TABLE `accounts` (`id` TEXT NOT NULL PRIMARY KEY, `workspaceId` TEXT NOT NULL, `name` TEXT NOT NULL)",
)

private fun SQLiteConnection.insertAccount(id: String, workspaceId: String, name: String) =
    prepare("INSERT INTO `accounts` (`id`, `workspaceId`, `name`) VALUES (?, ?, ?)").use { statement ->
        statement.bindText(1, id)
        statement.bindText(2, workspaceId)
        statement.bindText(3, name)
        statement.step()
    }

private fun SQLiteConnection.readSortOrders(): List<Pair<String, Int>> =
    prepare("SELECT `id`, `sortOrder` FROM `accounts` ORDER BY `workspaceId`, `sortOrder`").use { statement ->
        buildList {
            while (statement.step()) {
                add(statement.getText(0) to statement.getInt(1))
            }
        }
    }
