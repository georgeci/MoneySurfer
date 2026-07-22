package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind

/**
 * v25 → v26: give `categories` the `iconKey` and `hue` columns the creation screen has been
 * collecting and throwing away.
 *
 * Additive — two `ALTER TABLE ... ADD COLUMN`s, no table rebuild — followed by a deterministic
 * backfill. The backfill is the point: leaving existing rows on the column defaults would paint
 * every category the user already has in one identical colour, which reads as a broken screen
 * rather than as "no colour chosen". Deriving both values from a stable hash of the category id
 * (see [CategoryAppearance]) instead reproduces exactly what the pre-column UI drew, so the
 * upgrade is visually a no-op.
 *
 * The hash runs in Kotlin rather than SQL because SQLite has no portable string-hash function,
 * and it has to agree bit-for-bit with the one the domain uses on every platform.
 */
val MIGRATION_25_26: Migration = object : Migration(25, 26) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `categories` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE `categories` ADD COLUMN `hue` INTEGER NOT NULL DEFAULT -1")
        backfillCategoryAppearance(connection)
    }
}

/**
 * Reads every category id (with its system kind, so the seeded Transfer row keeps its fixed
 * appearance) and writes the derived icon key and hue back.
 */
internal fun backfillCategoryAppearance(connection: SQLiteConnection) {
    val rows = mutableListOf<Pair<String, CategorySystemKind?>>()
    connection.prepare("SELECT `id`, `systemKind` FROM `categories`").use { statement ->
        while (statement.step()) {
            val id = statement.getText(0)
            val systemKind = if (statement.isNull(1)) {
                null
            } else {
                runCatching { CategorySystemKind.valueOf(statement.getText(1)) }.getOrNull()
            }
            rows += id to systemKind
        }
    }

    connection.prepare("UPDATE `categories` SET `iconKey` = ?, `hue` = ? WHERE `id` = ?").use { statement ->
        rows.forEach { (id, systemKind) ->
            statement.reset()
            statement.bindText(BIND_ICON_KEY, CategoryAppearance.defaultIconKey(id, systemKind))
            statement.bindInt(BIND_HUE, CategoryAppearance.defaultHue(id, systemKind))
            statement.bindText(BIND_ID, id)
            statement.step()
        }
    }
}

// Bind indices of the UPDATE above — SQLite parameters are 1-based.
private const val BIND_ICON_KEY = 1
private const val BIND_HUE = 2
private const val BIND_ID = 3
