package com.georgeci.moneysurfer.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.georgeci.moneysurfer.data.db.dao.AccountDao
import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.db.dao.CategoryDao
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.data.db.dao.ExchangeRateDao
import com.georgeci.moneysurfer.data.db.dao.GoalContributionDao
import com.georgeci.moneysurfer.data.db.dao.GoalDao
import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import com.georgeci.moneysurfer.data.db.entity.ExchangeRateEntity
import com.georgeci.moneysurfer.data.db.entity.GoalContributionEntity
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionFtsEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity

/**
 * Schema version of [MoneySurferDatabase]. Single source of truth so the
 * Room annotation, the backup manifest, and tests can never drift.
 */
const val MONEY_SURFER_DB_VERSION: Int = 36

/**
 * The oldest schema version a released build must be able to upgrade *from*. Nothing below this
 * number can exist on a user's device, so those paths may stay unimplemented; every version *at
 * or above* it must be reachable by a hand-written [androidx.room.migration.Migration] chain, and
 * the `verifyRoomMigrations` Gradle task fails the build when a newly exported schema arrives
 * without one. See `docs/architecture/persistence.md` → "Room schema versioning".
 *
 * **The first release has not shipped yet, so this number is still provisional.** Until it does,
 * no database exists outside a developer machine and the baseline may legitimately move up with
 * [MONEY_SURFER_DB_VERSION] — a pre-release schema change can raise it instead of adding a
 * migration. The moment the first build reaches users this becomes a historical marker and must
 * never be raised again: raising it then would excuse exactly the migrations that real installs
 * depend on. Whoever cuts that release freezes this constant as part of it.
 */
const val MONEY_SURFER_DB_RELEASE_BASELINE_VERSION: Int = 36

@Database(
    entities = [
        UserEntity::class,
        WorkspaceEntity::class,
        WorkspaceMemberEntity::class,
        WorkspaceInviteEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransactionFtsEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        ExchangeRateEntity::class,
        ConfigEntryEntity::class,
    ],
    version = MONEY_SURFER_DB_VERSION,
)
@ConstructedBy(MoneySurferDatabaseConstructor::class)
abstract class MoneySurferDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun workspaceMemberDao(): WorkspaceMemberDao
    abstract fun workspaceInviteDao(): WorkspaceInviteDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun goalDao(): GoalDao
    abstract fun goalContributionDao(): GoalContributionDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun configEntryDao(): ConfigEntryDao
}

@Suppress("KotlinNoActualForExpect")
expect object MoneySurferDatabaseConstructor : RoomDatabaseConstructor<MoneySurferDatabase> {
    override fun initialize(): MoneySurferDatabase
}
