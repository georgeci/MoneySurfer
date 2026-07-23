package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private const val DEFAULT_HUE = 210

@Suppress("LongParameterList")
fun aGoal(
    id: GoalId = goalId(),
    workspaceId: WorkspaceId = workspaceId(),
    title: String = "New bike",
    emoji: String = "🚲",
    hue: Int = DEFAULT_HUE,
    target: Money = 1_000.dollars,
    currencyCode: CurrencyCode = USD,
    startDate: LocalDate = testDate,
    targetDate: LocalDate? = null,
    accountId: AccountId? = null,
    status: GoalStatus = GoalStatus.ACTIVE,
    createdAt: Instant = testInstant,
    updatedAt: Instant = createdAt,
): SavingsGoal = SavingsGoal(
    id = id,
    workspaceId = workspaceId,
    title = title,
    emoji = emoji,
    hue = hue,
    target = target,
    currencyCode = currencyCode,
    startDate = startDate,
    targetDate = targetDate,
    accountId = accountId,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Suppress("LongParameterList")
fun aGoalContribution(
    id: GoalContributionId = goalContributionId(),
    workspaceId: WorkspaceId = workspaceId(),
    goalId: GoalId = goalId(),
    amount: Money = 100.dollars,
    occurredOn: LocalDate = testDate,
    note: String = "",
    createdAt: Instant = testInstant,
    updatedAt: Instant = createdAt,
): GoalContribution = GoalContribution(
    id = id,
    workspaceId = workspaceId,
    goalId = goalId,
    amount = amount,
    occurredOn = occurredOn,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
