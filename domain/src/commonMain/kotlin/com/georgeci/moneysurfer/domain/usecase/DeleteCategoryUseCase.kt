package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import org.koin.core.annotation.Single

@Single
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {

    /** Deletes the category and returns the removed row so callers can offer an Undo. */
    suspend operator fun invoke(id: CategoryId): Category? {
        val existing = categoryRepository.getById(id) ?: return null
        categoryRepository.delete(id)
        return existing
    }
}
