package com.georgeci.moneysurfer.data.di

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
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
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.georgeci.moneysurfer.data")
class LocalDataModule {

    @Single
    fun userDao(database: MoneySurferDatabase): UserDao = database.userDao()

    @Single
    fun workspaceDao(database: MoneySurferDatabase): WorkspaceDao = database.workspaceDao()

    @Single
    fun workspaceMemberDao(database: MoneySurferDatabase): WorkspaceMemberDao = database.workspaceMemberDao()

    @Single
    fun workspaceInviteDao(database: MoneySurferDatabase): WorkspaceInviteDao = database.workspaceInviteDao()

    @Single
    fun accountDao(database: MoneySurferDatabase): AccountDao = database.accountDao()

    @Single
    fun categoryDao(database: MoneySurferDatabase): CategoryDao = database.categoryDao()

    @Single
    fun transactionDao(database: MoneySurferDatabase): TransactionDao = database.transactionDao()

    @Single
    fun budgetDao(database: MoneySurferDatabase): BudgetDao = database.budgetDao()

    @Single
    fun recurringRuleDao(database: MoneySurferDatabase): RecurringRuleDao = database.recurringRuleDao()

    @Single
    fun goalDao(database: MoneySurferDatabase): GoalDao = database.goalDao()

    @Single
    fun goalContributionDao(database: MoneySurferDatabase): GoalContributionDao = database.goalContributionDao()

    @Single
    fun exchangeRateDao(database: MoneySurferDatabase): ExchangeRateDao = database.exchangeRateDao()

    @Single
    fun configEntryDao(database: MoneySurferDatabase): ConfigEntryDao = database.configEntryDao()
}
