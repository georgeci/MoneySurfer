package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.CategoryDao
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [CategoryRepository::class])
class CategoryRepositoryImpl(
    private val dao: CategoryDao,
    private val outboxEnqueuer: OutboxEnqueuer,
    private val clock: ClockUseCase,
    private val timeFormatter: TimeFormatter,
) : CategoryRepository {

    override fun getAll(): Flow<List<Category>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> =
        dao.getByWorkspaceId(workspaceId.value).map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: CategoryId): Category? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(category: Category) {
        val entity = category.toEntity()
        dao.insert(entity)
        enqueueUpsert(entity, MutationOperation.INSERT)
    }

    override suspend fun update(category: Category) {
        val existingCreatedAt = dao.getById(category.id.value)?.createdAt
        val entity = category.toEntity().copy(
            createdAt = existingCreatedAt ?: category.toEntity().createdAt,
            updatedAt = clock.now().toEpochMilliseconds(),
        )
        dao.update(entity)
        enqueueUpsert(entity, MutationOperation.UPDATE)
    }

    override suspend fun delete(id: CategoryId) {
        val existing = dao.getById(id.value)
        dao.delete(id.value)
        if (existing != null) {
            outboxEnqueuer.enqueueDelete(
                entityType = SyncEntityTypes.CATEGORY,
                entityId = existing.id,
                scopeKey = existing.workspaceId,
            )
        }
    }

    private suspend fun enqueueUpsert(entity: CategoryEntity, operation: MutationOperation) {
        outboxEnqueuer.enqueueUpsert(
            entityType = SyncEntityTypes.CATEGORY,
            entityId = entity.id,
            scopeKey = entity.workspaceId,
            operation = operation,
        )
    }

    private fun CategoryEntity.toDomain() = Category(
        id = CategoryId(id),
        workspaceId = WorkspaceId(workspaceId),
        name = name,
        type = runCatching { CategoryType.valueOf(type) }.getOrDefault(CategoryType.EXPENSE),
        parentId = parentId?.let { CategoryId(it) },
        createdAt = timeFormatter.parseInstant(createdAt),
        updatedAt = timeFormatter.parseInstant(updatedAt),
    )

    private fun Category.toEntity() = CategoryEntity(
        id = id.value,
        workspaceId = workspaceId.value,
        name = name,
        type = type.name,
        parentId = parentId?.value,
        createdAt = timeFormatter.formatInstant(createdAt),
        updatedAt = timeFormatter.formatInstant(updatedAt),
    )
}
