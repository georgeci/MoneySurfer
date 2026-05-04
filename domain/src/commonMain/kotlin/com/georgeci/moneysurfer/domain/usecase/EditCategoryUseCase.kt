package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import org.koin.core.annotation.Single

@Single
class EditCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {

    suspend operator fun invoke(category: Category) {
        categoryRepository.update(category)
    }
}
