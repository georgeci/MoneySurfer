package com.georgeci.moneysurfer.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.georgeci.moneysurfer.data.backup.fixtures.deleteRecursively
import com.georgeci.moneysurfer.data.backup.fixtures.newTempDir
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okio.Path

/**
 * The release-vs-debug half of the migration policy — see
 * `docs/architecture/persistence.md` → "Room schema versioning".
 *
 * Both cases open a database file stamped at v20, a pre-baseline version with no migration path
 * to [MONEY_SURFER_DB_VERSION] (the declared chain starts at 25 → 26). That is the shape of a
 * schema bump shipped without its migration, which is precisely what the two builds must handle
 * differently: release refuses, debug wipes.
 */
class DatabaseBuilderSpec : StringSpec({

    "a release build refuses to open a database it has no migration path to" {
        withUnmigratableDb { path ->
            val database = getRoomDatabase(Room.databaseBuilder<MoneySurferDatabase>(path.toString()))
            try {
                // Room opens lazily, so the missing migration surfaces on first use, not on build().
                val failure = shouldThrowAny { runTest { database.accountDao().getById("any") } }
                // Pin the reason: without this the test would also pass on an unrelated I/O error.
                failure.messageChain() shouldContain "igration"
            } finally {
                database.close()
            }
        }
    }

    "a debug build drops the tables and carries on" {
        withUnmigratableDb { path ->
            val database = getRoomDatabase(
                builder = Room.databaseBuilder<MoneySurferDatabase>(path.toString()),
                allowDestructiveMigration = true,
            )
            try {
                runTest { database.accountDao().getById("any") shouldBe null }
            } finally {
                database.close()
            }
        }
    }

    "a release build opens a database already at the current schema and keeps its rows" {
        // The negative case above only proves it refuses. This is the everyday path: no version
        // change, so the release default must open normally rather than treat "no migration
        // needed" as "no migration found".
        withDbDir { dir ->
            val path = dir / "moneysurfer.db"
            seedConfigEntry(path)

            val database = getRoomDatabase(Room.databaseBuilder<MoneySurferDatabase>(path.toString()))
            try {
                runTest { database.configEntryDao().getByKey(SEEDED_KEY)?.value shouldBe SEEDED_VALUE }
            } finally {
                database.close()
            }
        }
    }

    "the debug fallback only fires on a mismatch — a matching schema keeps its rows" {
        // The risk of leaving the fallback on for debuggable hosts would be it wiping on every
        // launch. It must be inert whenever the schema already agrees.
        withDbDir { dir ->
            val path = dir / "moneysurfer.db"
            seedConfigEntry(path)

            val database = getRoomDatabase(
                builder = Room.databaseBuilder<MoneySurferDatabase>(path.toString()),
                allowDestructiveMigration = true,
            )
            try {
                runTest { database.configEntryDao().getByKey(SEEDED_KEY)?.value shouldBe SEEDED_VALUE }
            } finally {
                database.close()
            }
        }
    }

    "a fresh database is stamped with the declared schema version" {
        // Ties the constant to what Room actually writes, so a hand-edited @Database version or a
        // stale exported schema shows up here as well as in verifyRoomMigrations.
        withDbDir { dir ->
            val path = dir / "moneysurfer.db"
            seedConfigEntry(path)

            userVersionOf(path) shouldBe MONEY_SURFER_DB_VERSION
        }
    }

    "the release baseline never runs ahead of the schema it is a floor for" {
        // Deliberately not pinned to a literal: the first release has not shipped, so the
        // baseline may still move up with MONEY_SURFER_DB_VERSION (see the constant's KDoc).
        // What must hold either way is that it is a floor and not a ceiling — a baseline above
        // the live version would make verifyRoomMigrations check an empty range and wave
        // through a schema bump with no migration at all.
        (MONEY_SURFER_DB_RELEASE_BASELINE_VERSION <= MONEY_SURFER_DB_VERSION) shouldBe true
    }
})

private const val PRE_BASELINE_USER_VERSION = 20
private const val SEEDED_KEY = "ui.theme_mode"
private const val SEEDED_VALUE = "dark"

private fun withDbDir(block: (Path) -> Unit) {
    val dir = newTempDir("db-policy")
    try {
        block(dir)
    } finally {
        deleteRecursively(dir)
    }
}

/** Creates the database at the current schema and leaves one row in it. */
private fun seedConfigEntry(path: Path) {
    val database = getRoomDatabase(Room.databaseBuilder<MoneySurferDatabase>(path.toString()))
    try {
        runTest { database.configEntryDao().write(SEEDED_KEY, SEEDED_VALUE, updatedAt = 1L) }
    } finally {
        database.close()
    }
}

private fun userVersionOf(path: Path): Int {
    val connection = BundledSQLiteDriver().open(path.toString())
    return try {
        connection.prepare("PRAGMA user_version").use { statement ->
            statement.step()
            statement.getLong(0).toInt()
        }
    } finally {
        connection.close()
    }
}

/** Room wraps the migration failure, so the reason can sit on a cause rather than the top frame. */
private fun Throwable.messageChain(): String =
    generateSequence(this) { it.cause }.joinToString(" | ") { "${it::class.simpleName}: ${it.message}" }

private fun withUnmigratableDb(block: (Path) -> Unit) {
    val dir = newTempDir("db-policy")
    try {
        val path = dir / "moneysurfer.db"
        val connection = BundledSQLiteDriver().open(path.toString())
        try {
            connection.execSQL("CREATE TABLE stale (id INTEGER PRIMARY KEY)")
            connection.execSQL("PRAGMA user_version = $PRE_BASELINE_USER_VERSION")
        } finally {
            connection.close()
        }
        block(path)
    } finally {
        deleteRecursively(dir)
    }
}
