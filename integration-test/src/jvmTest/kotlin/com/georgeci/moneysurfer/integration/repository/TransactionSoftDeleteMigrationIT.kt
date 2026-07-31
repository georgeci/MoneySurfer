package com.georgeci.moneysurfer.integration.repository

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_35_36
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The v36 addition against a real SQLite.
 *
 * Worth pinning despite being a one-line `ALTER TABLE`: on debuggable hosts the builder still ends
 * in `fallbackToDestructiveMigration(dropAllTables = true)`, so a migration that does not leave the
 * schema Room expects does not fail loudly — it drops every table on the next launch, taking the
 * user's ledger with it. Release builds opt out of that fallback and crash on open instead (see
 * `docs/architecture/persistence.md` → "Room schema versioning").
 * The column's type and nullability are checked because Room validates both
 * at open time, and every existing row is checked to still be live, because a backfill that
 * accidentally set `deletedAt` would hide the entire history behind the new filter.
 */
class TransactionSoftDeleteMigrationIT : StringSpec({

    "the migration adds a nullable deletedAt column to transactions" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV35Transactions()

            MIGRATION_35_36.migrate(connection)

            connection.deletedAtColumn() shouldBe DeletedAtColumn(type = "INTEGER", notNull = false)
        } finally {
            connection.close()
        }
    }

    "rows that predate the column are live, not deleted" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV35Transactions()
            connection.execSQL("INSERT INTO `transactions` (`id`, `note`) VALUES ('t-1', 'rent')")

            MIGRATION_35_36.migrate(connection)

            connection.singleText("SELECT COUNT(*) FROM `transactions`") shouldBe "1"
            connection.singleText("SELECT COUNT(*) FROM `transactions` WHERE `deletedAt` IS NULL") shouldBe "1"
            // The column is an addition, not a rewrite — the row's own data is still there.
            connection.singleText("SELECT `note` FROM `transactions`") shouldBe "rent"
        } finally {
            connection.close()
        }
    }
})

private data class DeletedAtColumn(val type: String, val notNull: Boolean)

/** Enough of the v35 table to add a column to; the rest of the schema is not what is under test. */
private fun SQLiteConnection.createV35Transactions() =
    execSQL("CREATE TABLE `transactions` (`id` TEXT NOT NULL PRIMARY KEY, `note` TEXT)")

private fun SQLiteConnection.deletedAtColumn(): DeletedAtColumn? =
    prepare("PRAGMA table_info(`transactions`)").use { statement ->
        while (statement.step()) {
            if (statement.getText(1) == "deletedAt") {
                return DeletedAtColumn(type = statement.getText(2), notNull = statement.getInt(3) == 1)
            }
        }
        null
    }

private fun SQLiteConnection.singleText(sql: String): String =
    prepare(sql).use { statement ->
        statement.step()
        statement.getText(0)
    }
