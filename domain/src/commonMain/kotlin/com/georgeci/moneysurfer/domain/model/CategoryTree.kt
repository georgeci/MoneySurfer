package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType

/** A category placed in the tree, carrying how deep under a root it sits. */
data class CategoryNode(
    val category: Category,
    val depth: Int,
)

/**
 * Shapes a flat category list into the parent/child structure both the manage screen and the
 * parent picker need. Kept in the domain because the rule it encodes — a category may not
 * become its own ancestor — is a data-integrity rule, not a rendering detail.
 */
object CategoryTree {

    /** Nesting depth the UI renders. Deeper parents are refused by [eligibleParents]. */
    const val MAX_DEPTH: Int = 1

    /**
     * Flattens to display order: each root immediately followed by its children.
     *
     * A category whose `parentId` names a row that is not in [categories] is treated as a root.
     * That is not defensive padding — sync can legitimately deliver a child before its parent,
     * and dropping the orphan would make a category vanish from the manage screen entirely.
     */
    fun flatten(categories: List<Category>): List<CategoryNode> {
        val present = categories.mapTo(mutableSetOf()) { it.id }
        val childrenByParent = categories
            .filter { it.parentId != null && it.parentId in present }
            .groupBy { requireNotNull(it.parentId) }
        val roots = categories.filter { it.parentId == null || it.parentId !in present }

        return buildList {
            roots.forEach { root ->
                add(CategoryNode(root, depth = 0))
                childrenByParent[root.id].orEmpty().forEach { child ->
                    add(CategoryNode(child, depth = 1))
                }
            }
        }
    }

    /** Direct children of [parentId], in the order [categories] arrived. */
    fun childrenOf(categories: List<Category>, parentId: CategoryId): List<Category> =
        categories.filter { it.parentId == parentId }

    /**
     * Every category reachable downwards from [rootId], excluding [rootId] itself. Walks
     * iteratively with a visited set so a cycle already in the data — which sync can deliver
     * even though the UI refuses to create one — terminates instead of hanging the screen.
     */
    fun descendantsOf(categories: List<Category>, rootId: CategoryId): Set<CategoryId> {
        val childrenByParent = categories
            .filter { it.parentId != null }
            .groupBy { requireNotNull(it.parentId) }
        val found = mutableSetOf<CategoryId>()
        val queue = ArrayDeque(childrenByParent[rootId].orEmpty().map { it.id })
        while (queue.isNotEmpty()) {
            val next = queue.removeFirst()
            if (!found.add(next)) continue
            queue += childrenByParent[next].orEmpty().map { it.id }
        }
        return found
    }

    /**
     * Categories that may be picked as the parent of [categoryId] (null when creating).
     *
     * Excludes: a different type (an expense category under an income parent is meaningless),
     * system categories, the category itself and everything below it (a category cannot be its
     * own ancestor), and any category already nested [MAX_DEPTH] deep.
     */
    fun eligibleParents(
        categories: List<Category>,
        categoryId: CategoryId?,
        type: CategoryType,
    ): List<Category> {
        val excluded = categoryId
            ?.let { descendantsOf(categories, it) + it }
            .orEmpty()
        val byId = categories.associateBy { it.id }
        return categories.filter { candidate ->
            candidate.type == type &&
                candidate.systemKind == null &&
                candidate.id !in excluded &&
                depthOf(candidate, byId) < MAX_DEPTH
        }
    }

    private fun depthOf(category: Category, byId: Map<CategoryId, Category>): Int {
        var depth = 0
        var parentId = category.parentId
        val seen = mutableSetOf(category.id)
        while (parentId != null && seen.add(parentId)) {
            depth++
            parentId = byId[parentId]?.parentId
        }
        return depth
    }
}
