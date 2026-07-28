package com.georgeci.moneysurfer.integration.repository

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_34_35
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * The v35 upgrade against a real SQLite.
 *
 * Two things have to hold for a migrated database to keep working where a freshly created one
 * does: rows that pre-date splits must read back as `NULL` (they are single-category transactions,
 * which is exactly what null means), and the index has to exist — Room validates the schema at open
 * time, so a missing index is a crash on the first launch after the upgrade rather than a slow
 * query.
 */
class TransactionSplitIdMigrationIT : StringSpec({

    "existing rows keep their data and gain a null splitId" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV34Transactions()
            connection.insertTransaction(id = "t-1", note = "milk")
            connection.insertTransaction(id = "t-2", note = "rent")

            MIGRATION_34_35.migrate(connection)

            connection.readSplitIds() shouldBe listOf("t-1" to null, "t-2" to null)
        } finally {
            connection.close()
        }
    }

    "the split id becomes writable and reads back" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV34Transactions()
            connection.insertTransaction(id = "leg-1", note = "Pyaterochka")

            MIGRATION_34_35.migrate(connection)
            connection.execSQL("UPDATE `transactions` SET `splitId` = 'sp-1' WHERE `id` = 'leg-1'")

            connection.readSplitIds() shouldBe listOf("leg-1" to "sp-1")
        } finally {
            connection.close()
        }
    }

    "the index the list query relies on is created with the column" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV34Transactions()

            MIGRATION_34_35.migrate(connection)

            connection.indexNames() shouldContain "index_transactions_splitId"
        } finally {
            connection.close()
        }
    }
})

/** The columns of `transactions` this migration touches, as they stand at v34. */
private fun SQLiteConnection.createV34Transactions() = execSQL(
    "CREATE TABLE `transactions` (`id` TEXT NOT NULL PRIMARY KEY, `note` TEXT NOT NULL)",
)

private fun SQLiteConnection.insertTransaction(id: String, note: String) =
    prepare("INSERT INTO `transactions` (`id`, `note`) VALUES (?, ?)").use { statement ->
        statement.bindText(1, id)
        statement.bindText(2, note)
        statement.step()
    }

private fun SQLiteConnection.readSplitIds(): List<Pair<String, String?>> =
    prepare("SELECT `id`, `splitId` FROM `transactions` ORDER BY `id`").use { statement ->
        buildList {
            while (statement.step()) {
                add(statement.getText(0) to statement.getText(1).takeIf { !statement.isNull(1) })
            }
        }
    }

private fun SQLiteConnection.indexNames(): List<String> =
    prepare("SELECT `name` FROM sqlite_master WHERE `type` = 'index' AND `tbl_name` = 'transactions'")
        .use { statement ->
            buildList {
                while (statement.step()) add(statement.getText(0))
            }
        }
