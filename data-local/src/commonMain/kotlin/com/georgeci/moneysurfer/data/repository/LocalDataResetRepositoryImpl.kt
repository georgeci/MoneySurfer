package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.AccountDao
import com.georgeci.moneysurfer.data.db.dao.BudgetDao
import com.georgeci.moneysurfer.data.db.dao.CategoryDao
import com.georgeci.moneysurfer.data.db.dao.RecurringRuleDao
import com.georgeci.moneysurfer.data.db.dao.TransactionDao
import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import org.koin.core.annotation.Single

@Single(binds = [LocalDataResetRepository::class])
class LocalDataResetRepositoryImpl(
    private val recurringRuleDao: RecurringRuleDao,
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val workspaceInviteDao: WorkspaceInviteDao,
    private val workspaceMemberDao: WorkspaceMemberDao,
    private val workspaceDao: WorkspaceDao,
    private val userDao: UserDao,
) : LocalDataResetRepository {

    // FK-safe order: leaf tables first, then parents.
    override suspend fun clearAll() {
        recurringRuleDao.deleteAll()
        budgetDao.deleteAll()
        transactionDao.deleteAll()
        accountDao.deleteAll()
        categoryDao.deleteAll()
        workspaceInviteDao.deleteAll()
        workspaceMemberDao.deleteAll()
        workspaceDao.deleteAll()
        userDao.deleteAll()
    }
}
