package com.georgeci.moneysurfer.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.georgeci.moneysurfer.data.db.dao.AccountDao
import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.db.dao.CategoryDao
import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity

@Database(
    entities = [
        UserEntity::class,
        WorkspaceEntity::class,
        WorkspaceMemberEntity::class,
        WorkspaceInviteEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
    ],
    version = 19,
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
}

@Suppress("KotlinNoActualForExpect")
expect object MoneySurferDatabaseConstructor : RoomDatabaseConstructor<MoneySurferDatabase> {
    override fun initialize(): MoneySurferDatabase
}
