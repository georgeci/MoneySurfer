package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetCategoriesUseCase(
    private val categoryRepository: CategoryRepository,
    private val session: SessionPointers,
) {

    operator fun invoke(): Flow<List<Category>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId?.let(categoryRepository::getByWorkspaceId) ?: flowOf(emptyList())
        }
}
