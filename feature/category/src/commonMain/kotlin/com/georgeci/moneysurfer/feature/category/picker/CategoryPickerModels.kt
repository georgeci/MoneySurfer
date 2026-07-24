package com.georgeci.moneysurfer.feature.category.picker

import com.georgeci.moneysurfer.domain.primitives.CategoryId

/**
 * The two picker layouts the design specifies. Both sit behind the same
 * [CategoryChooserBottomSheet] entry point; the caller picks one per navigation.
 */
enum class CategoryPickerVariant {
    /** Flat 4-column grid of every category of the selected type, parents and children mixed. */
    GRID,

    /** Parent cards whose children expand inline underneath them. */
    TREE,
    ;

    companion object {
        /**
         * Parses the route argument. An unknown or absent value degrades to [GRID] rather than
         * throwing — the route is serialized into the back stack and can outlive a rename.
         */
        fun fromRouteArg(value: String?): CategoryPickerVariant =
            entries.firstOrNull { it.name == value } ?: GRID
    }
}

/**
 * One category as the picker draws it.
 *
 * Appearance stays as the category's stored `iconKey`/`hue` instead of a resolved icon and
 * colour, so everything up to the composable is plain data the state tests can assert on.
 */
data class CategoryPickerItem(
    val id: CategoryId,
    val name: String,
    val iconKey: String,
    val hue: Int,
    val systemKind: String?,
    val selected: Boolean,
)

/**
 * A top-level category and the children the tree variant currently shows under it.
 *
 * [childCount] counts *all* children, not just the visible ones, because it feeds the
 * `N subcategories` subline, which must not shrink while a search is narrowing [children].
 */
data class CategoryTreeGroup(
    val parent: CategoryPickerItem,
    val childCount: Int = 0,
    val expanded: Boolean = false,
    val children: List<CategoryPickerItem> = emptyList(),
)
