package com.georgeci.moneysurfer.integration.repository

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_33_34
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The v34 addition against a real SQLite.
 *
 * Worth pinning despite being a bare `CREATE TABLE`: the builder ends in
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a migration that fails to produce the
 * table Room expects does not fail loudly — it drops every table on the next launch. The columns
 * and their nullability are checked against what `ConfigEntryEntity` declares, because Room
 * validates the schema at open time and a mismatch is the same silent wipe.
 */
class ConfigEntryMigrationIT : StringSpec({

    "the migration creates config_entry with the columns the entity declares" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV33Remnant()

            MIGRATION_33_34.migrate(connection)

            connection.columnsOf("config_entry") shouldContainExactly listOf(
                ColumnInfo(name = "key", type = "TEXT", notNull = true, primaryKey = 1),
                ColumnInfo(name = "value", type = "TEXT", notNull = true, primaryKey = 0),
                ColumnInfo(name = "updatedAt", type = "INTEGER", notNull = true, primaryKey = 0),
                // Nullable: a row that has never been pushed is exactly what the sign-in
                // reconciliation looks for.
                ColumnInfo(name = "lastPushedAt", type = "INTEGER", notNull = false, primaryKey = 0),
            )
        } finally {
            connection.close()
        }
    }

    "existing data is untouched — the migration is a pure addition" {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV33Remnant()
            connection.execSQL("INSERT INTO `accounts` (`id`) VALUES ('a-1')")

            MIGRATION_33_34.migrate(connection)

            connection.singleText("SELECT `id` FROM `accounts`") shouldBe "a-1"
            connection.singleText("SELECT COUNT(*) FROM `config_entry`") shouldBe "0"
        } finally {
            connection.close()
        }
    }

    "re-running the migration is a no-op rather than an error" {
        // `CREATE TABLE IF NOT EXISTS` — a partially applied upgrade retried on the next launch
        // must not abort and drop the database.
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.createV33Remnant()

            MIGRATION_33_34.migrate(connection)
            MIGRATION_33_34.migrate(connection)

            connection.singleText("SELECT COUNT(*) FROM `config_entry`") shouldBe "0"
        } finally {
            connection.close()
        }
    }
})

private data class ColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Int,
)

/** Any pre-existing table is enough to prove the migration leaves the rest of the schema alone. */
private fun SQLiteConnection.createV33Remnant() =
    execSQL("CREATE TABLE `accounts` (`id` TEXT NOT NULL PRIMARY KEY)")

private fun SQLiteConnection.columnsOf(table: String): List<ColumnInfo> =
    prepare("PRAGMA table_info(`$table`)").use { statement ->
        buildList {
            while (statement.step()) {
                add(
                    ColumnInfo(
                        name = statement.getText(1),
                        type = statement.getText(2),
                        notNull = statement.getInt(3) == 1,
                        primaryKey = statement.getInt(5),
                    ),
                )
            }
        }
    }

private fun SQLiteConnection.singleText(sql: String): String =
    prepare(sql).use { statement ->
        statement.step()
        statement.getText(0)
    }
