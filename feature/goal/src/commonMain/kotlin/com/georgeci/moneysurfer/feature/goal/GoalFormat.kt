package com.georgeci.moneysurfer.feature.goal

import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalStatus
import kotlinx.datetime.LocalDate
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_status_active
import moneysurfer.feature.goal.generated.resources.goal_status_archived
import moneysurfer.feature.goal.generated.resources.goal_status_completed
import moneysurfer.feature.goal.generated.resources.goal_status_paused
import org.jetbrains.compose.resources.StringResource

/** The design system carries its own status enum so `uikit` stays free of `domain`. */
internal fun GoalStatus.toUi(): SurferGoalStatus = when (this) {
    GoalStatus.ACTIVE -> SurferGoalStatus.Active
    GoalStatus.COMPLETED -> SurferGoalStatus.Completed
    GoalStatus.PAUSED -> SurferGoalStatus.Paused
    GoalStatus.ARCHIVED -> SurferGoalStatus.Archived
}

internal fun GoalStatus.labelRes(): StringResource = when (this) {
    GoalStatus.ACTIVE -> Res.string.goal_status_active
    GoalStatus.COMPLETED -> Res.string.goal_status_completed
    GoalStatus.PAUSED -> Res.string.goal_status_paused
    GoalStatus.ARCHIVED -> Res.string.goal_status_archived
}

/** `05 Aug 2026` — the same shape the transaction screens use. */
internal fun formatGoalDate(date: LocalDate): String {
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(MONTH_ABBREVIATION)
    val day = date.day.toString().padStart(2, '0')
    return "$day $month ${date.year}"
}

private const val MONTH_ABBREVIATION = 3

/**
 * Text-field input for goal amounts: digits and at most one decimal separator. Goal amounts are
 * never negative — the withdraw screen flips the sign itself — so a minus is simply dropped.
 */
internal object GoalAmountInput {

    fun sanitize(raw: String): String {
        val digits = StringBuilder()
        var separatorUsed = false
        for (ch in raw) {
            when {
                ch.isDigit() -> digits.append(ch)
                (ch == '.' || ch == ',') && !separatorUsed -> {
                    separatorUsed = true
                    digits.append('.')
                }
            }
        }
        return digits.toString()
    }

    /** `null` while the field is empty or still half-typed (`"."`). */
    fun parse(value: String): Money? = value.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
        ?.let(Money::fromDouble)
}
