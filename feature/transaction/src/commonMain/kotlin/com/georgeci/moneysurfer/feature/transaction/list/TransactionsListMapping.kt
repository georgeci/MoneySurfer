package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
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
    rows.groupBy { it.transaction.operationDate }
        .map { (date, txns) ->
            val net = txns.fold(Money.zero()) { acc, t -> acc + t.signedMoney() }
            TransactionGroupUi(
                date = date,
                dateLabel = dateLabel(date),
                netFormatted = MoneyFormatter.format(net, txns.first().transaction.currencyCode),
                netPositive = !net.isNegative(),
                transactions = txns.map { it.toRow(accountNames) },
            )
        }

private fun CategorizedTransaction.toRow(accountNames: Map<AccountId, String>): TransactionRowUi {
    val isTransferLeg = transaction.transferId != null
    return TransactionRowUi(
        id = transaction.id,
        // Merchant first: "Starbucks" identifies the row better than whatever was jotted next
        // to it, and the note still shows when there is no merchant.
        title = transaction.merchant.ifBlank { transaction.note }.ifBlank { categoryName.orEmpty() },
        subtitle = categoryName.orEmpty(),
        accountName = accountNames[transaction.accountId].orEmpty(),
        formattedAmount = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode),
        isExpense = transaction.type == TransactionType.EXPENSE,
        isTransfer = isTransferLeg,
        // A transfer leg is drawn from the shared transfer palette instead of its category's
        // hue, so it carries no seed — see how the screen renders it.
        categoryHueSeed = if (isTransferLeg) {
            ""
        } else {
            categoryName ?: transaction.categoryId?.value.orEmpty()
        },
    )
}

private fun CategorizedTransaction.signedMoney(): Money = when (transaction.type) {
    TransactionType.EXPENSE -> -transaction.money.abs()
    else -> transaction.money.abs()
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
