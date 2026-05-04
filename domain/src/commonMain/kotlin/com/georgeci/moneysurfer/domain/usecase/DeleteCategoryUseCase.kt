package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import org.koin.core.annotation.Single

@Single
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {

    suspend operator fun invoke(id: CategoryId) {
        categoryRepository.delete(id)
    }
}
