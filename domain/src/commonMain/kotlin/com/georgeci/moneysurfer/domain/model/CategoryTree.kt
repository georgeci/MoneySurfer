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

    /**
     * Deepest indent the UI draws. [eligibleParents] refuses to create anything deeper, but
     * [flatten] still *renders* deeper rows — it just stops indenting them.
     */
    const val MAX_DEPTH: Int = 1

    /**
     * Flattens to display order: every category, depth-first, each one immediately followed by
     * its subtree.
     *
     * Every input category comes out exactly once. That is the whole contract, and it is worth
     * stating because the shapes this has to survive are not the shapes the UI can create:
     *
     * - A child whose parent is not in [categories] is treated as a root — sync legitimately
     *   delivers a child before its parent.
     * - A subtree deeper than [MAX_DEPTH] is rendered in full, with the indent clamped. The UI
     *   will not build one, but another client's write can.
     * - A category caught in a parent cycle is reachable from no root at all. It is emitted at
     *   the top level once the real roots are exhausted.
     *
     * In every one of those cases the alternative is a category that silently disappears from
     * the manage screen, which is strictly worse than one drawn at the wrong indent.
     */
    fun flatten(categories: List<Category>): List<CategoryNode> {
        val present = categories.mapTo(mutableSetOf()) { it.id }
        val childrenByParent = categories
            .filter { it.parentId != null && it.parentId in present }
            .groupBy { requireNotNull(it.parentId) }
        val (roots, nested) = categories.partition { it.parentId == null || it.parentId !in present }

        val emitted = mutableSetOf<CategoryId>()
        val result = mutableListOf<CategoryNode>()
        val stack = ArrayDeque<CategoryNode>()

        // Real roots first; the nested pass then picks up only what no root reached, i.e. the
        // members of a parent cycle. Everything else is already emitted and skipped.
        (roots + nested).forEach { seed ->
            if (seed.id in emitted) return@forEach
            stack.addLast(CategoryNode(seed, depth = 0))
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                if (!emitted.add(node.category.id)) continue
                result += node
                val childDepth = (node.depth + 1).coerceAtMost(MAX_DEPTH)
                childrenByParent[node.category.id].orEmpty().asReversed().forEach { child ->
                    stack.addLast(CategoryNode(child, childDepth))
                }
            }
        }
        return result
    }

    /** Direct children of [parentId], in the order [categories] arrived. */
    fun childrenOf(categories: List<Category>, parentId: CategoryId): List<Category> =
        categories.filter { it.parentId == parentId }

    /**
     * Every category reachable downwards from [rootId].
     *
     * [rootId] itself is in the result only when it is reachable from itself — i.e. it sits in a
     * parent cycle, which sync can deliver even though the UI refuses to create one. That is the
     * useful answer rather than an edge case to paper over: the one caller, [eligibleParents],
     * uses this to decide what a category may *not* be parented to, and a category inside a
     * cycle must not be offered as its own parent either.
     *
     * The walk is iterative and keeps a visited set, so a cycle terminates instead of hanging.
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
