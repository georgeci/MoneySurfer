package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

/**
 * Stable, human-readable id factories for tests.
 *
 * Prefer these over `*.uuid()` so failing assertions read clearly
 * (`AccountId("a-1")` instead of a random uuid).
 */
fun userId(value: String = "u-1"): UserId = UserId(value)
fun workspaceId(value: String = "ws-1"): WorkspaceId = WorkspaceId(value)
fun accountId(value: String = "a-1"): AccountId = AccountId(value)
fun categoryId(value: String = "c-1"): CategoryId = CategoryId(value)
fun transactionId(value: String = "t-1"): TransactionId = TransactionId(value)
fun transferId(value: String = "tr-1"): TransferId = TransferId(value)
fun splitId(value: String = "sp-1"): SplitId = SplitId(value)
fun budgetId(value: String = "b-1"): BudgetId = BudgetId(value)
fun goalId(value: String = "g-1"): GoalId = GoalId(value)
fun goalContributionId(value: String = "gc-1"): GoalContributionId = GoalContributionId(value)
fun recurringRuleId(value: String = "r-1"): RecurringRuleId = RecurringRuleId(value)

/** Sequential id generator — useful when a test needs N distinct ids of one type. */
class SequentialIds(private val prefix: String) {
    private var counter = 0
    fun next(): String = "$prefix-${++counter}"
}
