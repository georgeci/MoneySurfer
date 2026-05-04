package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun getAll(): Flow<List<Workspace>>
    fun getByUserId(userId: UserId): Flow<List<Workspace>>
    suspend fun getById(id: WorkspaceId): Workspace?
    suspend fun insert(workspace: Workspace)
    suspend fun update(workspace: Workspace)
    suspend fun delete(id: WorkspaceId)
}
