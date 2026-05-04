package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun getAll(): Flow<List<Category>>
    fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>>
    suspend fun getById(id: CategoryId): Category?
    suspend fun insert(category: Category)
    suspend fun update(category: Category)
    suspend fun delete(id: CategoryId)
}
