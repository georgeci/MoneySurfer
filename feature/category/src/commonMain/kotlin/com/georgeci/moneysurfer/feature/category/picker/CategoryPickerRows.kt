package com.georgeci.moneysurfer.feature.category.picker

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType

/**
 * Turns the raw category list into what each picker variant renders.
 *
 * Kept as pure functions rather than folded into the composables so both shapes — the flat grid
 * and the parent/child tree — are asserted by the state tests instead of by eyeballing a sheet.
 */
internal object CategoryPickerRows {

    /** Type the sheet opens on: what the caller pinned, else the selection's own type. */
    fun initialType(
        categories: List<Category>,
        selectedId: CategoryId?,
        filterType: CategoryType?,
    ): CategoryType = filterType
        ?: categories.firstOrNull { it.id == selectedId }?.type
        ?: CategoryType.EXPENSE

    /**
     * Parents that start expanded: the one holding the current selection, so a selected child is
     * on screen the moment the sheet opens instead of hidden behind a chevron.
     */
    fun expandedForSelection(categories: List<Category>, selectedId: CategoryId?): Set<CategoryId> =
        categories.firstOrNull { it.id == selectedId }?.parentId?.let(::setOf).orEmpty()

    /** Variant A: every category of [type], hierarchy flattened away, in arrival order. */
    fun grid(
        categories: List<Category>,
        type: CategoryType,
        query: String,
        selectedId: CategoryId?,
    ): List<CategoryPickerItem> = categories
        .filter { it.type == type && it.matches(query) }
        .map { it.toItem(selectedId) }

    /**
     * Variant B: top-level categories of [type], each carrying the children it currently shows.
     *
     * A category whose parent is missing from [categories] — filtered out by type, or not yet
     * synced — is drawn at the top level rather than dropped, the same call `CategoryTree.flatten`
     * makes on the manage screen.
     *
     * Search overrides [expandedIds]: a parent is kept when it matches or holds a match, and it is
     * force-expanded down to the matching children, since a hit the user cannot see is no hit.
     */
    fun tree(
        categories: List<Category>,
        type: CategoryType,
        query: String,
        selectedId: CategoryId?,
        expandedIds: Set<CategoryId>,
    ): List<CategoryTreeGroup> {
        val ofType = categories.filter { it.type == type }
        val present = ofType.mapTo(mutableSetOf()) { it.id }
        val childrenByParent = ofType
            .filter { it.parentId != null && it.parentId in present }
            .groupBy { requireNotNull(it.parentId) }
        val roots = ofType.filter { it.parentId == null || it.parentId !in present }
        val searching = query.isNotBlank()

        return roots.mapNotNull { root ->
            val children = childrenByParent[root.id].orEmpty()
            val visible = if (searching) children.filter { it.matches(query) } else children
            if (searching && !root.matches(query) && visible.isEmpty()) return@mapNotNull null

            val expanded = if (searching) visible.isNotEmpty() else root.id in expandedIds
            CategoryTreeGroup(
                parent = root.toItem(selectedId),
                childCount = children.size,
                expanded = expanded,
                children = if (expanded) visible.map { it.toItem(selectedId) } else emptyList(),
            )
        }
    }

    private fun Category.matches(query: String): Boolean =
        query.isBlank() || name.contains(query.trim(), ignoreCase = true)

    private fun Category.toItem(selectedId: CategoryId?) = CategoryPickerItem(
        id = id,
        name = name,
        iconKey = iconKey,
        hue = hue,
        systemKind = systemKind?.name,
        selected = id == selectedId,
    )
}
