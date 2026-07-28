package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.usecase.CreateSplitTransactionUseCase

/**
 * Pure state transitions of the split editor and of the category chooser that feeds it.
 *
 * Kept beside the ViewModel rather than inside it: every one of these is a `Content → Content`
 * function of its arguments alone, so they read (and test) as the small pieces of arithmetic they
 * are, and the ViewModel keeps only the work that needs a repository, a side effect or the screen's
 * key counter.
 */

/**
 * Opens the split editor seeded with two lines — the minimum a split is — the first carrying
 * whatever category the form already had, or closes it again, discarding the lines.
 *
 * Nothing about the receipt itself changes: the amount, account, note and date stay put, and the
 * type segment keeps deciding whether the legs are expenses or income.
 */
internal fun TransactionCreationState.Content.withSplitToggled(
    nextKey: () -> Int,
): TransactionCreationState.Content = if (isSplit) {
    copy(splitLines = emptyList())
} else {
    copy(
        splitLines = listOf(
            TransactionSplitLineUi(key = nextKey(), category = selectedCategory),
            TransactionSplitLineUi(key = nextKey()),
        ),
    )
}

/**
 * Appends a line, keeping the figure the previous last line was showing.
 *
 * The trailing line renders the remainder and is read-only, so it never stores what it displays.
 * Without this the line demoted by the new one would come back blank — the amount the user could
 * see a moment ago would silently move to the new line and leave a zero leg behind that only
 * disables Save, with nothing on screen pointing at it. An over-assigned remainder is not pinned:
 * it is negative, the editor is already reporting that, and storing it would flip its sign.
 */
internal fun TransactionCreationState.Content.withSplitLineAdded(
    key: Int,
): TransactionCreationState.Content {
    val remainder = splitRemainder
    val pinned = splitLines.mapIndexed { index, line ->
        if (index == splitLines.lastIndex && remainder.isPositive()) {
            line.copy(amount = remainder.toAmountInput())
        } else {
            line
        }
    }
    return copy(splitLines = pinned + TransactionSplitLineUi(key = key))
}

/**
 * Removes one line, and closes the editor when fewer than [MIN_SPLIT_LINES] are left: a single line
 * is not a split, and leaving one behind would show an editor whose Save button silently refuses.
 */
internal fun TransactionCreationState.Content.withSplitLineRemoved(
    key: Int,
): TransactionCreationState.Content {
    val remaining = splitLines.filterNot { it.key == key }
    return copy(splitLines = if (remaining.size < MIN_SPLIT_LINES) emptyList() else remaining)
}

internal fun TransactionCreationState.Content.withSplitLineAmount(
    key: Int,
    amount: String,
): TransactionCreationState.Content = copy(
    splitLines = splitLines.map { line ->
        if (line.key == key) line.copy(amount = amount) else line
    },
)

/** The legs a save would write: each line's category with its share of the amount. */
internal fun TransactionCreationState.Content.splitLegs(): List<CreateSplitTransactionUseCase.Leg> =
    splitLines.zip(splitAmounts) { line, money ->
        CreateSplitTransactionUseCase.Leg(categoryId = line.category?.id, money = money)
    }

/** What [slot] currently holds — the fallback when a picked category cannot be resolved. */
internal fun TransactionCreationState.Content.categoryInSlot(slot: CategorySlot): Category? =
    when (slot) {
        CategorySlot.Single -> selectedCategory
        is CategorySlot.SplitLine -> splitLines.firstOrNull { it.key == slot.key }?.category
    }

internal fun TransactionCreationState.Content.withPickedCategory(
    categories: List<Category>,
    selected: Category?,
    slot: CategorySlot,
): TransactionCreationState.Content = when (slot) {
    CategorySlot.Single -> withSelectedCategory(categories, selected)
    // A split line's category is the line's alone: the grid's selection and the "most used"
    // ordering both describe the single-category form, and moving them would leave the form
    // pointing at whichever line the user edited last.
    is CategorySlot.SplitLine -> copy(
        categories = categories,
        splitLines = splitLines.map { line ->
            if (line.key == slot.key) line.copy(category = selected) else line
        },
    )
}

internal fun TransactionCreationState.Content.withSelectedCategory(
    categories: List<Category>,
    selected: Category?,
): TransactionCreationState.Content = copy(
    categories = categories,
    selectedCategory = selected,
    displayCategories = buildDisplayCategories(
        categories = categories,
        counts = categoryUsageCounts,
        type = type.categoryType(),
        selected = selected,
    ),
)
