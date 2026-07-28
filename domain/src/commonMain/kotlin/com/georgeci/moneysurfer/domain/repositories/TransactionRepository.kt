package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow

// Transactions are the app's central entity and every screen queries them along a different axis
// (account, workspace, window, transfer pair). Splitting the interface to satisfy the threshold
// would scatter one storage contract across several types without removing a single query.
@Suppress("TooManyFunctions")
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

    /**
     * Every leg sharing [transferId] — two for a healthy transfer, one for a leg whose pair was
     * deleted or never imported. Callers must not assume a pair exists.
     */
    suspend fun getByTransferId(transferId: TransferId): List<Transaction>

    /**
     * Every leg sharing [splitId], oldest first — the receipt the row belongs to.
     *
     * A one-element result is a legitimate state, not a broken group: a leg can arrive from sync or
     * a CSV import before (or without) its siblings, and it is a complete transaction on its own.
     */
    suspend fun getBySplitId(splitId: SplitId): List<Transaction>
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: TransactionId)
}
