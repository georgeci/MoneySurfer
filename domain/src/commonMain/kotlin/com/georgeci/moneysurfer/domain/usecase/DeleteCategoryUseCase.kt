package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * What a delete removed, in enough detail to undo it: the category itself plus its children
 * **as they were before**, still pointing at the deleted parent.
 */
data class CategoryDeletion(
    val category: Category,
    val reparentedChildren: List<Category>,
)

@Single
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {

    /**
     * Deletes the category and returns what was removed so callers can offer an Undo.
     *
     * Deleting a parent moves its children up to the root rather than removing them with it —
     * the alternative, refusing the delete while children exist, makes the user dismantle a
     * tree from the bottom for no gain. The reparenting also has to happen *first*: the
     * `parentId` foreign key declares no cascade, so deleting a row that still has children
     * would be rejected by SQLite.
     */
    suspend operator fun invoke(id: CategoryId): CategoryDeletion? {
        val existing = categoryRepository.getById(id) ?: return null
        val children = CategoryTree.childrenOf(categoryRepository.getAll().first(), id)
        children.forEach { child ->
            categoryRepository.update(child.copy(parentId = null))
        }
        categoryRepository.delete(id)
        return CategoryDeletion(category = existing, reparentedChildren = children)
    }
}
