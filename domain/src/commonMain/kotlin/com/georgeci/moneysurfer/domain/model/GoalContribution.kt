package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One movement against a [SavingsGoal]. [amount] is signed — a negative amount
 * is a withdrawal, not a separate entity and not a separate flag.
 *
 * [transferId] is always `null` in v1: no money moves and `Transaction` is not
 * touched. It exists so the money-backed model can later store the id of a real
 * transfer here without a schema change on either side of the wire.
 */
data class GoalContribution(
    val id: GoalContributionId = GoalContributionId.uuid(),
    val workspaceId: WorkspaceId,
    val goalId: GoalId,
    val amount: Money,
    val occurredOn: LocalDate,
    val note: String = "",
    val transferId: TransferId? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)
