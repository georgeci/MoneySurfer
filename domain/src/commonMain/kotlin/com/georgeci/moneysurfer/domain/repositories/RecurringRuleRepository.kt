package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface RecurringRuleRepository {
    fun getAll(): Flow<List<RecurringRule>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<RecurringRule>>
    suspend fun getById(id: RecurringRuleId): RecurringRule?
    suspend fun insert(rule: RecurringRule)
    suspend fun update(rule: RecurringRule)
    suspend fun delete(id: RecurringRuleId)
}
