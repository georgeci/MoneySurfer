package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getByAccountId(accountId: AccountId): Flow<List<Transaction>>

    /**
     * Newest-first page of categorized transactions inside [window], for one account or — when
     * [accountId] is `null` — every account.
     *
     * At most [limit] rows are returned: the caller grows the limit to page further rather than
     * offsetting, so a row inserted while the user scrolls cannot shift the already-visible
     * prefix. An unbounded [window] is still bounded by [limit].
     */
    fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>>

    /**
     * Per-type, per-currency sums over the *whole* [window] — unaffected by the paging applied to
     * [getCategorizedWindow], so a summary built from these stays correct as pages load.
     */
    fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>>
    suspend fun getById(id: TransactionId): Transaction?
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: TransactionId)
}
