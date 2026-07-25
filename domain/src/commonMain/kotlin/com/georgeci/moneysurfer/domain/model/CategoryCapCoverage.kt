package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId

/**
 * What the category form's monthly-cap shortcut is allowed to do for one category.
 *
 * Budgets owns limits — `Category` carries no `cap` field (md/categories.md, decision 1). A
 * per-category cap *is* a budget naming exactly that one category, so the shortcut may only ever
 * touch such a budget. It must never add a second limit on top of one the user already built on
 * the Budgets screen, which would leave two caps on one category and no defined winner.
 */
sealed interface CategoryCapCoverage {

    /**
     * The control is an editable amount field. [budget] is the single-category budget backing the
     * cap today, or null when there is none and saving an amount should create one.
     */
    data class Editable(val budget: Budget?) : CategoryCapCoverage

    /**
     * A limit the shortcut must not compete with already covers this category. The control goes
     * read-only and names [budget]; the user changes the limit on the Budgets screen instead.
     */
    data class Managed(val budget: Budget) : CategoryCapCoverage
}

/**
 * Resolves which budget, if any, the monthly-cap control on the category form speaks for.
 *
 * Two kinds of budget are deliberately not treated as coverage:
 * - **archived** ones (`isActive = false`) limit nothing, so a cap set here is a fresh budget
 *   rather than the resurrection of an old one;
 * - the **all-categories** budget (empty `categoryIds`) is the global spending envelope, which by
 *   design sits above per-category budgets rather than competing with them — treating it as
 *   coverage would leave the shortcut permanently dead for anyone who keeps a general budget.
 *
 * Everything else that names this category counts. A budget naming it *alongside other
 * categories*, or several budgets naming it at once, both resolve to [CategoryCapCoverage.Managed].
 */
fun resolveCategoryCapCoverage(
    categoryId: CategoryId?,
    budgets: List<Budget>,
): CategoryCapCoverage {
    // A category that does not exist yet cannot be covered by anything.
    if (categoryId == null) return CategoryCapCoverage.Editable(budget = null)

    val covering = budgets
        .filter { it.isActive && categoryId in it.categoryIds }
        // Stable order so the read-only notice always names the same budget across recompositions.
        .sortedWith(compareBy({ it.createdAt }, { it.id.value }))

    val only = covering.singleOrNull()
    return when {
        covering.isEmpty() -> CategoryCapCoverage.Editable(budget = null)
        only != null && only.categoryIds.size == 1 -> CategoryCapCoverage.Editable(budget = only)
        else -> CategoryCapCoverage.Managed(budget = covering.first())
    }
}
