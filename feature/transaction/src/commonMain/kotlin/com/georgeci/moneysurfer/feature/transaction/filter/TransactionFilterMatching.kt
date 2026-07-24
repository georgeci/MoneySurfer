package com.georgeci.moneysurfer.feature.transaction.filter

import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.domain.util.startOfIsoWeek
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private const val MINOR_PER_MAJOR = 100L
private const val MAX_DECIMALS = 2
private const val DAYS_PER_WEEK = 7

/** `Money.MAX_MINOR` is 10^15 minor units, i.e. 13 digits of major units. */
private const val MAX_WHOLE_DIGITS = 13

private const val DECEMBER = 12
private const val DAYS_IN_DECEMBER = 31
private const val DECIMAL_RADIX = 10

/**
 * The one date window the list loads, whoever asked for it.
 *
 * [TransactionDateRange.FollowPeriod] defers to the pager ([mode] + [anchor]); every other range
 * takes over completely. The pager is hidden while it is not the source, so the two can never end
 * up as competing windows.
 */
fun resolveWindow(
    dateRange: TransactionDateRange,
    mode: TransactionPeriodMode,
    anchor: LocalDate,
    today: LocalDate,
): TransactionPeriodWindow = when (dateRange) {
    TransactionDateRange.FollowPeriod -> periodWindow(mode, anchor)
    is TransactionDateRange.Preset -> presetWindow(dateRange.preset, today)
    is TransactionDateRange.Custom -> TransactionPeriodWindow(from = dateRange.from, to = dateRange.to)
}

private fun presetWindow(preset: TransactionDatePreset, today: LocalDate): TransactionPeriodWindow =
    when (preset) {
        TransactionDatePreset.Today -> TransactionPeriodWindow(today, today)
        TransactionDatePreset.Yesterday -> today.minus(1, DateTimeUnit.DAY)
            .let { TransactionPeriodWindow(it, it) }
        TransactionDatePreset.ThisWeek -> today.startOfIsoWeek()
            .let { TransactionPeriodWindow(it, it.plus(DAYS_PER_WEEK - 1, DateTimeUnit.DAY)) }
        TransactionDatePreset.ThisMonth -> periodWindow(TransactionPeriodMode.Month, today)
        TransactionDatePreset.LastMonth -> periodWindow(
            TransactionPeriodMode.Month,
            LocalDate(today.year, today.month, 1).minus(1, DateTimeUnit.MONTH),
        )
        TransactionDatePreset.ThisYear -> TransactionPeriodWindow(
            from = LocalDate(today.year, 1, 1),
            to = LocalDate(today.year, DECEMBER, DAYS_IN_DECEMBER),
        )
    }

/**
 * An amount the user typed, as an inclusive range of minor units.
 *
 * Typed precision is the tolerance: "12" means "twelve-something" (1200..1299), "12.5" means
 * "twelve fifty-something" (1250..1259), "12.50" is exact. Without that rule a search for the
 * amount on a receipt would only ever hit whole-unit transactions, and the min/max fields would
 * exclude a €12.99 charge from a "max 12" bound the user meant as "no more than twelve-ish".
 */
data class AmountRange(val fromMinor: Long, val toMinor: Long)

/**
 * Parses [raw] as an amount, or returns null when it is not one.
 *
 * Currency symbols, spaces and thousands separators are dropped, and both `.` and `,` read as the
 * decimal separator — a filter field is not the place to be strict about how a number is written.
 * More than [MAX_DECIMALS] fraction digits is rejected rather than rounded: it is not an amount
 * this app can hold, so silently matching a truncated one would be a lie.
 */
fun parseAmountQuery(raw: String): AmountRange? {
    val normalized = normalizeAmountInput(raw) ?: return null
    val whole = normalized.substringBefore('.')
    val fraction = normalized.substringAfter('.', missingDelimiterValue = "")
    val from = whole.toLong() * MINOR_PER_MAJOR + fraction.padEnd(MAX_DECIMALS, '0').toLong()
    // Everything the untyped digits could still be: "12" covers 12.00 through 12.99.
    val slack = pow10(MAX_DECIMALS - fraction.length) - 1
    return AmountRange(fromMinor = from, toMinor = from + slack)
}

/**
 * `raw` reduced to `digits[.digits]`, or null when it is not an amount at all. Rejects rather
 * than clamps anything wider than the domain's own cap ([MAX_WHOLE_DIGITS] major units), so the
 * arithmetic above cannot overflow on a pasted wall of digits.
 */
private fun normalizeAmountInput(raw: String): String? {
    val cleaned = raw.trim()
        .filterNot { it.isWhitespace() || it == '\'' || it == '_' }
        .removePrefix("+")
        .replace(',', '.')
    val whole = cleaned.substringBefore('.')
    val fraction = cleaned.substringAfter('.', missingDelimiterValue = "")
    val wellFormed = cleaned.all { it.isDigit() || it == '.' } &&
        cleaned.count { it == '.' } <= 1 &&
        whole.isNotEmpty() &&
        whole.length <= MAX_WHOLE_DIGITS &&
        fraction.length <= MAX_DECIMALS
    return cleaned.takeIf { wellFormed }
}

private fun pow10(exponent: Int): Long = generateSequence(1L) { it * DECIMAL_RADIX }.elementAt(exponent)

/**
 * Whether [row] survives these filters.
 *
 * The date window is *not* checked here — it is applied by the query (see [resolveWindow]), so
 * re-checking it would only duplicate a bound the database already honoured.
 *
 * `accountIds` is expected to be empty when the list is already scoped to one account; the
 * ViewModel clears it there, because intersecting a per-account list with a different account
 * would silently show nothing.
 */
fun TransactionFilters.matches(row: CategorizedTransaction): Boolean {
    val transaction = row.transaction
    return listOf(
        matchesType(row),
        accountIds.isEmpty() || transaction.accountId in accountIds,
        categoryIds.isEmpty() || transaction.categoryId in categoryIds,
        matchesAmountBounds(row),
        !recurringOnly || transaction.recurringRuleId != null,
        !plannedOnly || transaction.status == TransactionStatus.PLANNED,
        matchesQuery(row),
    ).all { it }
}

private fun TransactionFilters.matchesType(row: CategorizedTransaction): Boolean = when (type) {
    TransactionTypeFilter.All -> true
    TransactionTypeFilter.Expenses -> row.transaction.type == TransactionType.EXPENSE
    TransactionTypeFilter.Income -> row.transaction.type == TransactionType.INCOME
}

/**
 * Bounds compare against the *magnitude*: the sign is the type filter's business, and a user
 * asking for "at least 50" means fifty of money, not −50 being below the bound.
 *
 * An unparseable bound is ignored rather than treated as zero, and a trailing separator reads
 * as the digits before it, so neither nonsense nor a half-typed "12." empties the list.
 */
private fun TransactionFilters.matchesAmountBounds(row: CategorizedTransaction): Boolean {
    val minor = row.transaction.money.abs().minor
    val min = parseAmountQuery(minAmount)
    val max = parseAmountQuery(maxAmount)
    return (min == null || minor >= min.fromMinor) && (max == null || minor <= max.toMinor)
}

/**
 * Free-text search over what the user can actually see or remember about a row: merchant, note,
 * category, tags — and the amount, which is matched numerically rather than as text so that "12"
 * finds €12.40 without also finding every note containing "12".
 */
private fun TransactionFilters.matchesQuery(row: CategorizedTransaction): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    val transaction = row.transaction
    val textHit = sequenceOf(transaction.merchant, transaction.note, row.categoryName.orEmpty())
        .plus(transaction.tags)
        .any { it.contains(needle, ignoreCase = true) }
    val amount = parseAmountQuery(needle)
    val amountHit = amount != null &&
        transaction.money.abs().minor in amount.fromMinor..amount.toMinor
    return textHit || amountHit
}
