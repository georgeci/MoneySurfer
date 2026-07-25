package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import org.koin.core.annotation.Single

/**
 * Takes the database rather than one DAO per table — the parameter list grew
 * with every new table and bought nothing, since this class only ever fans out
 * `deleteAll()`.
 */
@Single(binds = [LocalDataResetRepository::class])
class LocalDataResetRepositoryImpl(
    private val database: MoneySurferDatabase,
) : LocalDataResetRepository {

    // FK-safe order: leaf tables first, then parents.
    override suspend fun clearAll() {
        database.goalContributionDao().deleteAll()
        database.goalDao().deleteAll()
        database.recurringRuleDao().deleteAll()
        database.budgetDao().deleteAll()
        database.transactionDao().deleteAll()
        database.accountDao().deleteAll()
        database.categoryDao().deleteAll()
        database.workspaceInviteDao().deleteAll()
        database.workspaceMemberDao().deleteAll()
        database.workspaceDao().deleteAll()
        database.userDao().deleteAll()
        // Not user data — public FX quotes — but "reset local data" should leave no rows behind,
        // and the cost of forgetting them is a single refetch on the next dashboard open.
        database.exchangeRateDao().deleteAll()
    }
}
