package com.georgeci.moneysurfer.data.db

import androidx.room.migration.Migration
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_25_26
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_27_28
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_29_30
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_30_31
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_31_32
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_32_33
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_33_34
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_34_35
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_35_36
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Shape of the declared migration chain — the half `verifyRoomMigrations` cannot see.
 *
 * The Gradle gate greps for the *name* `MIGRATION_<n>_<n+1>` and for its presence in
 * `addMigrations(...)`. It has no way to know what versions the object behind that name actually
 * declares, so `val MIGRATION_34_35 = object : Migration(35, 36)` — the copy-paste that comes
 * from starting a new migration by duplicating the last one — passes the gate and then silently
 * leaves 34 → 35 unhandled at runtime. That is what these assertions close.
 *
 * See `docs/architecture/persistence.md` → "Room schema versioning".
 */
class MigrationChainSpec : StringSpec({

    "every migration declares the versions its name promises" {
        declaredMigrations.forEach { (name, migration) ->
            val (from, to) = name.removePrefix("MIGRATION_").split("_").map(String::toInt)
            withClue(name) {
                migration.startVersion shouldBe from
                migration.endVersion shouldBe to
            }
        }
    }

    "every migration moves exactly one version forward" {
        // A Migration(33, 36) would satisfy the name check above only if named that way, but a
        // multi-version jump anywhere in the chain leaves the intermediate exported schemas
        // unreachable and breaks the one-file-per-change convention the policy is built on.
        declaredMigrations.forEach { (name, migration) ->
            withClue(name) { migration.endVersion shouldBe migration.startVersion + 1 }
        }
    }

    "no two migrations start from the same version" {
        // Room picks a path by start version; duplicates make which one runs an ordering accident.
        val duplicated = declaredMigrations
            .groupBy { (_, migration) -> migration.startVersion }
            .filterValues { it.size > 1 }
            .keys
        duplicated.shouldBeEmpty()
    }

    "the chain is contiguous from the release baseline to the current schema" {
        // The runtime mirror of the Gradle gate: every step a released install could face has a
        // migration. Vacuous while baseline == current, and live the moment either moves.
        val covered = declaredMigrations.map { (_, migration) -> migration.startVersion }.toSet()
        val missing = (MONEY_SURFER_DB_RELEASE_BASELINE_VERSION until MONEY_SURFER_DB_VERSION)
            .filterNot { it in covered }
        missing.shouldBeEmpty()
    }

    "the chain ends at the current schema version" {
        // Guards the bump-without-migration case from the other side: the newest migration must
        // land exactly on the version the @Database annotation declares.
        declaredMigrations.maxOf { (_, migration) -> migration.endVersion } shouldBe
            MONEY_SURFER_DB_VERSION
    }
})

/**
 * Every migration the module declares, paired with its source name. Listed by hand rather than
 * discovered reflectively: the name is exactly what the assertions check the object against, so
 * deriving one from the other would make the test agree with itself.
 */
private val declaredMigrations: List<Pair<String, Migration>> = listOf(
    "MIGRATION_25_26" to MIGRATION_25_26,
    "MIGRATION_27_28" to MIGRATION_27_28,
    "MIGRATION_29_30" to MIGRATION_29_30,
    "MIGRATION_30_31" to MIGRATION_30_31,
    "MIGRATION_31_32" to MIGRATION_31_32,
    "MIGRATION_32_33" to MIGRATION_32_33,
    "MIGRATION_33_34" to MIGRATION_33_34,
    "MIGRATION_34_35" to MIGRATION_34_35,
    "MIGRATION_35_36" to MIGRATION_35_36,
)
