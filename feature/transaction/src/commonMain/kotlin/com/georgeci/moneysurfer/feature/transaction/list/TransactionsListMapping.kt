package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import kotlinx.datetime.LocalDate

/**
 * The chip rail's values. Names are resolved from the account and category lists rather than in
 * the screen, which has neither; a single selection shows its name, several show their count, and
 * nothing selected leaves the chip in its bare "Account" state.
 */
internal fun chips(
    filters: TransactionFilters,
    accounts: List<Account>,
    categories: List<Category>,
): TransactionFilterChipsUi = TransactionFilterChipsUi(
    dateRange = filters.dateRange,
    type = filters.type,
    accountCount = filters.accountIds.size,
    accountName = filters.accountIds.singleOrNull()
        ?.let { id -> accounts.firstOrNull { it.id == id }?.name },
    categoryCount = filters.categoryIds.size,
    categoryName = filters.categoryIds.singleOrNull()
        ?.let { id -> categories.firstOrNull { it.id == id }?.name },
    sort = filters.sort,
)

internal fun groupByDate(
    rows: List<CategorizedTransaction>,
    accountNames: Map<AccountId, String>,
    dateLabel: (LocalDate) -> TransactionDateUi,
): List<TransactionGroupUi> =
    collapseSplitLegs(rows)
        .groupBy { it.primary.transaction.operationDate }
        .map { (date, receipts) ->
            val net = receipts.fold(Money.zero()) { acc, receipt -> acc + receipt.signedMoney() }
            TransactionGroupUi(
                date = date,
                dateLabel = dateLabel(date),
                netFormatted = MoneyFormatter.format(
                    net,
                    receipts.first().primary.transaction.currencyCode,
                ),
                netPositive = !net.isNegative(),
                transactions = receipts.map { it.toRow(accountNames) },
            )
        }

/**
 * One line of the list: an ordinary transaction, or every leg of a split the page holds in full.
 *
 * The legs of a split share account, date and type by construction (see
 * `CreateSplitTransactionUseCase`), so a receipt has one date to group under and one currency to
 * format in.
 */
internal data class ListReceipt(val legs: List<CategorizedTransaction>) {
    val primary: CategorizedTransaction get() = legs.first()
    val isSplit: Boolean get() = legs.size > 1

    /** Distinct categories the receipt was filed under — what the collapsed row's badge counts. */
    val categoryCount: Int get() = legs.map { it.transaction.categoryId }.distinct().size
}

/**
 * Collapses the legs of a split into a single receipt — a supermarket run paid for once should be
 * one row, not one row per category it was filed under.
 *
 * **Only a group present in full collapses.** A page is a `LIMIT` window and may be narrowed
 * further by an in-memory filter, so a group can easily be here in part: cut by the paging
 * boundary, or thinned down to the one leg a category filter matched. Collapsing those would render
 * a total quietly missing money — a figure that changes on its own when the user taps "load more".
 * Rendering the legs it actually has instead is honest at every window size, and it is also what
 * the account- and category-scoped lists want, where a leg genuinely is its own row.
 *
 * [CategorizedTransaction.splitLegCount] is the group's size in storage, counted by the window
 * query, which is what makes "present in full" answerable without a second round trip.
 */
internal fun collapseSplitLegs(rows: List<CategorizedTransaction>): List<ListReceipt> {
    val presentLegs = rows
        .filter { it.transaction.splitId != null }
        .groupBy { it.transaction.splitId }
    val collapsed = mutableSetOf<SplitId>()
    return rows.mapNotNull { row ->
        val splitId = row.transaction.splitId ?: return@mapNotNull ListReceipt(listOf(row))
        val legs = presentLegs[splitId].orEmpty()
        if (legs.size < row.splitLegCount) return@mapNotNull ListReceipt(listOf(row))
        if (!collapsed.add(splitId)) return@mapNotNull null
        ListReceipt(legs)
    }
}

private fun ListReceipt.toRow(accountNames: Map<AccountId, String>): TransactionRowUi {
    val transaction = primary.transaction
    val isTransferLeg = transaction.transferId != null
    val total = legs.fold(Money.zero()) { acc, leg -> acc + leg.transaction.money.abs() }
    return TransactionRowUi(
        id = transaction.id,
        // Merchant first: "Starbucks" identifies the row better than whatever was jotted next
        // to it, and the note still shows when there is no merchant.
        title = transaction.merchant.ifBlank { transaction.note }
            .ifBlank { primary.categoryName.orEmpty() },
        // A collapsed receipt names no single category — the screen replaces this with the
        // category count, which is the one thing the row can say truthfully about all of them.
        subtitle = primary.categoryName.orEmpty(),
        splitCategoryCount = if (isSplit) categoryCount else 0,
        accountName = accountNames[transaction.accountId].orEmpty(),
        formattedAmount = MoneyFormatter.format(total, transaction.currencyCode),
        isExpense = transaction.type == TransactionType.EXPENSE,
        isTransfer = isTransferLeg,
        // A transfer leg is drawn from the shared transfer palette instead of its category's
        // hue, so it carries no seed — see how the screen renders it.
        categoryHueSeed = if (isTransferLeg) {
            ""
        } else {
            primary.categoryName ?: transaction.categoryId?.value.orEmpty()
        },
    )
}

/** The receipt's contribution to the day's net — every leg of it, signed by its type. */
private fun ListReceipt.signedMoney(): Money =
    legs.fold(Money.zero()) { acc, leg ->
        val magnitude = leg.transaction.money.abs()
        acc + if (leg.transaction.type == TransactionType.EXPENSE) -magnitude else magnitude
    }

/**
 * The single currency the summary strip renders in.
 *
 * Scoped to an account it is simply that account's currency. In the all-accounts view there is
 * no one right answer, so the currency with the largest total magnitude is chosen — a
 * deterministic, recency-independent pick, so a newly-added transaction in another currency
 * cannot flip the strip the way keying off the newest visible row did. Amounts in the other
 * currencies are still excluded from the total (see [buildSummary]); a per-currency summary
 * would be a larger design change than this screen owns.
 */
internal fun summaryCurrency(account: Account?, totals: List<TransactionTotal>): CurrencyCode =
    account?.currencyCode
        ?: totals.maxByOrNull { it.total.minor }?.currencyCode
        ?: CurrencyCode("USD")

/**
 * Summary of the whole period, not of the loaded page: [totals] come straight from the
 * database aggregation, so paging cannot change these numbers.
 *
 * It also deliberately ignores the filters. The strip answers "what happened in this period",
 * which stays true while the user narrows the list below it; recomputing it from the matched
 * page would instead answer "what is on screen", and would drift as pages load.
 *
 * Rows in other currencies are dropped rather than added to the displayed one — mixing minor
 * units across currencies would produce a confidently wrong number.
 */
internal fun buildSummary(
    totals: List<TransactionTotal>,
    currency: CurrencyCode,
): TransactionSummaryUi {
    val inCurrency = totals.filter { it.currencyCode == currency }
    val income = inCurrency.sumOfMoney { it.type == TransactionType.INCOME }
    val expense = inCurrency.sumOfMoney { it.type == TransactionType.EXPENSE }
    val net = income - expense
    return TransactionSummaryUi(
        incomeFormatted = "+" + MoneyFormatter.format(income, currency),
        expenseFormatted = "−" + MoneyFormatter.format(expense, currency),
        netFormatted = (if (net.isNegative()) "−" else "+") +
            MoneyFormatter.format(net.abs(), currency),
        netPositive = !net.isNegative(),
    )
}

private fun List<TransactionTotal>.sumOfMoney(predicate: (TransactionTotal) -> Boolean): Money =
    filter(predicate).fold(Money.zero()) { acc, total -> acc + total.total }
