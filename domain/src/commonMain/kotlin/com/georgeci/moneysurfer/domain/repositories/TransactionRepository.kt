package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun getAll(): Flow<List<Transaction>>
    fun getAllCategorized(): Flow<List<CategorizedTransaction>>
    fun getByAccountId(accountId: AccountId): Flow<List<Transaction>>
    fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>>
    suspend fun getById(id: TransactionId): Transaction?
    suspend fun insert(transaction: Transaction)
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: TransactionId)
}
