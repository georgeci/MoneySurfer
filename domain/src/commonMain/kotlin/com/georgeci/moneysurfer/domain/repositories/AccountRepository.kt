package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

// Ten operations, and the tenth trips the interface threshold. Splitting them would only move the
// line: every one is a single-column write on the same row, and the callers — use cases that own
// one account action each — would then have to name two repositories to do one thing.
@Suppress("TooManyFunctions")
interface AccountRepository {
    fun getAll(): Flow<List<Account>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>>
    suspend fun getById(id: AccountId): Account?
    suspend fun insert(account: Account)
    suspend fun update(account: Account)
    suspend fun delete(id: AccountId)

    /**
     * Increment the cached `balance` of [accountId] by [delta]. Used by total-calc to keep
     * `Account.balance` in sync with transaction changes without rewriting the whole row.
     *
     * Negative delta is allowed (subtracts). The repository writes the result and bumps
     * `updatedAt`. Does NOT enqueue an outbox row — balance is a derived projection that
     * Firestore recomputes server-side from its own tx state (see md/totatl_calc.md §6).
     */
    suspend fun applyDelta(accountId: AccountId, delta: Money)

    /**
     * Overwrite the cached balance with [balance]. Used by `RecalculateAccountBalanceUseCase`
     * to repair the cache from authoritative transaction history. Like [applyDelta], does
     * not enqueue an outbox row.
     */
    suspend fun setBalance(accountId: AccountId, balance: Money)

    /**
     * Toggle the archived flag on [accountId]. Bumps `updatedAt` and enqueues a sync update.
     * Archived accounts keep their transactions but are hidden from active lists and excluded
     * from total/budget rollups. No-op if [accountId] is unknown locally.
     */
    suspend fun setArchived(accountId: AccountId, archived: Boolean)

    /**
     * Rewrite `sortOrder` so the accounts named in [orderedIds] sit in exactly that order,
     * starting at 0. Ids the workspace does not know are ignored; accounts left out keep the
     * value they had. Only the rows whose position actually changed are written and enqueued
     * for sync, so dropping a row back where it started costs nothing.
     */
    suspend fun reorder(orderedIds: List<AccountId>)
}
