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

    "the frozen release baseline is the version the first release shipped" {
        // Guards the constant the verifyRoomMigrations Gradle task reads: lowering it would
        // silently excuse a missing migration, raising it would excuse every one below the
        // new floor.
        MONEY_SURFER_DB_RELEASE_BASELINE_VERSION shouldBe 36
        (MONEY_SURFER_DB_VERSION >= MONEY_SURFER_DB_RELEASE_BASELINE_VERSION) shouldBe true
    }
})

private const val PRE_BASELINE_USER_VERSION = 20

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
