package com.georgeci.moneysurfer.feature.transaction.filter

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import kotlinx.datetime.LocalDate

/**
 * The type segment. Shared by the list's chip rail and the filter screen's segmented control —
 * both edit the same field of [TransactionFilters], so there is one enum, not two.
 */
enum class TransactionTypeFilter { All, Expenses, Income }

/**
 * The sort order set implied by the design's `Sort: Newest` chip.
 *
 * Deliberately only the two date directions. The list is grouped under day headers, and an
 * amount-ordered list has no coherent day grouping — it would either repeat a header per row or
 * silently drop the grouping the rest of the screen is built on. Amount ordering is a flat-list
 * mode, which is a larger change than this screen owns.
 */
enum class TransactionSort { Newest, Oldest }

/** The presets on the filter screen's date row. `Custom` is [TransactionDateRange.Custom]. */
enum class TransactionDatePreset { Today, Yesterday, ThisWeek, ThisMonth, LastMonth, ThisYear }

/**
 * Where the list's date window comes from.
 *
 * There is exactly one source at a time: [FollowPeriod] hands the window to the period pager,
 * anything else takes it over and the pager is hidden. Letting both drive it would produce two
 * controls silently overwriting each other's window — see `resolveWindow`.
 */
sealed interface TransactionDateRange {
    /** The pager owns the window: its mode and anchor decide the bounds. */
    data object FollowPeriod : TransactionDateRange

    data class Preset(val preset: TransactionDatePreset) : TransactionDateRange

    /** Either bound may be null — an open-ended range is a legitimate thing to ask for. */
    data class Custom(val from: LocalDate?, val to: LocalDate?) : TransactionDateRange
}

/**
 * Everything the transactions list is narrowed by, hoisted above both the list and the filter
 * screen (see [TransactionFilterStore]).
 *
 * Amount bounds stay as raw text rather than parsed numbers: the field content is what the filter
 * screen re-renders when the user comes back to it, and a half-typed "12." must survive that
 * round trip. Parsing happens at match time, in `parseAmountQuery`.
 */
data class TransactionFilters(
    val query: String = "",
    val dateRange: TransactionDateRange = TransactionDateRange.FollowPeriod,
    val type: TransactionTypeFilter = TransactionTypeFilter.All,
    val accountIds: Set<AccountId> = emptySet(),
    val categoryIds: Set<CategoryId> = emptySet(),
    val minAmount: String = "",
    val maxAmount: String = "",
    val recurringOnly: Boolean = false,
    val plannedOnly: Boolean = false,
    val sort: TransactionSort = TransactionSort.Newest,
) {

    /**
     * What the badge on the filter button counts.
     *
     * [query] is excluded on purpose: the search field is visible right next to the badge and
     * shows its own state, so counting it would double-report the same fact. The amount bounds
     * count as one filter — they are one row on the filter screen.
     */
    val activeCount: Int
        get() = listOf(
            dateRange != TransactionDateRange.FollowPeriod,
            type != TransactionTypeFilter.All,
            accountIds.isNotEmpty(),
            categoryIds.isNotEmpty(),
            minAmount.isNotBlank() || maxAmount.isNotBlank(),
            recurringOnly,
            plannedOnly,
            sort != TransactionSort.Newest,
        ).count { it }

    /** Whether an empty list is the filters' doing rather than an empty account. */
    val isNarrowed: Boolean get() = activeCount > 0 || query.isNotBlank()

    /** `Reset` on the filter screen, and what the list falls back to. Keeps the search text. */
    fun cleared(): TransactionFilters = TransactionFilters(query = query)

    companion object {
        val Empty = TransactionFilters()
    }
}
