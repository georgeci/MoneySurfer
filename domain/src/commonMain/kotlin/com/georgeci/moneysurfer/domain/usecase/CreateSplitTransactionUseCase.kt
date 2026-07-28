package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single
import kotlin.time.Instant

/** Fewer legs than this is not a split — it is an ordinary transaction, created the ordinary way. */
private const val MIN_SPLIT_LEGS = 2

/**
 * Writes one receipt as N sibling transactions sharing a [SplitId] — groceries and household
 * chemicals from the same supermarket run, each landing in its own category.
 *
 * Every leg is a complete transaction, so budgets, monthly totals, spend history and the account
 * balance need no split-awareness at all: a leg filed under "Household chemicals" is indistinguishable
 * from an ordinary transaction to all of them. That is the whole reason a split is sibling rows
 * rather than a child allocation table (the other half of the reason is sync: per-entity LWW cannot
 * keep a parent and its children consistent when they are pulled independently). The trade-offs are
 * written up in `docs/plans/split-transaction-across-categories.md`.
 *
 * The invariant this establishes is that the legs of a group differ **only** in category and
 * amount: account, currency, business date, moment and type are the receipt's, shared by all of
 * them. [UpdateTransactionUseCase] re-establishes it whenever a single leg is later edited through
 * the ordinary edit path, so a group cannot drift into two payments.
 */
@Single
class CreateSplitTransactionUseCase(
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
    private val getCurrentTime: GetCurrentTimeUseCase,
) {

    /** One allocation of the receipt: where it is filed and how much of the payment it took. */
    data class Leg(val categoryId: CategoryId?, val money: Money)

    data class Params(
        val account: Account,
        val legs: List<Leg>,
        val note: String,
        val merchant: String = "",
        val tags: List<String> = emptyList(),
        val operationAt: Instant,
        val operationDate: LocalDate,
        val type: TransactionType,
        val status: TransactionStatus = TransactionStatus.ACTUAL,
    )

    /**
     * Returns the legs as written, oldest first — the caller can hand them straight to
     * [DeleteTransactionUseCase]'s Undo counterpart or assert on them in a test.
     *
     * Throws [IllegalArgumentException] on a group that could not be one receipt: fewer than two
     * legs, or a leg carrying no money. Both are programmer errors — the creation screen keeps its
     * Save button disabled until neither holds.
     */
    suspend operator fun invoke(params: Params): List<Transaction> {
        require(params.legs.size >= MIN_SPLIT_LEGS) {
            "A split needs at least $MIN_SPLIT_LEGS legs, got ${params.legs.size}"
        }
        require(params.legs.none { it.money.abs().isZero() }) { "A split leg must carry an amount" }

        val now = getCurrentTime()
        val splitId = SplitId.uuid()
        val rows = params.legs.map { leg ->
            Transaction(
                id = TransactionId.uuid(),
                workspaceId = params.account.workspaceId,
                accountId = params.account.id,
                money = leg.money.abs(),
                currencyCode = params.account.currencyCode,
                categoryId = leg.categoryId,
                note = params.note,
                merchant = params.merchant,
                tags = params.tags,
                operationAt = params.operationAt,
                operationDate = params.operationDate,
                type = params.type,
                status = params.status,
                createdAt = now,
                updatedAt = now,
                splitId = splitId,
            )
        }
        rows.forEach { applyTransactionChange(old = null, new = it) }
        return rows
    }
}
