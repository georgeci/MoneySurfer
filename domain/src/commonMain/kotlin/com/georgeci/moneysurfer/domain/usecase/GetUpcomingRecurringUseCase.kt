package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.UpcomingRecurring
import com.georgeci.moneysurfer.domain.model.upcomingOccurrences
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.RecurringRuleRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The next few scheduled payments of the active workspace, soonest first — what the dashboard's
 * upcoming card lists.
 *
 * Every occurrence is computed from the rule's own schedule (see
 * [com.georgeci.moneysurfer.domain.model.nextOccurrenceOnOrAfter]), so the list is right as soon as
 * the rules themselves are: a device that has pulled a rule can date it without waiting for the
 * generator to run. Empty — not absent — while no workspace is selected or its base currency has
 * not arrived yet, which is the card's own "nothing scheduled" state.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetUpcomingRecurringUseCase(
    private val recurringRuleRepository: RecurringRuleRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
    private val clock: ClockUseCase,
) {

    operator fun invoke(
        limit: Int = UPCOMING_LIMIT,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Flow<List<UpcomingRecurring>> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            workspaceId ?: return@flatMapLatest flowOf(emptyList())

            combine(
                recurringRuleRepository.getByWorkspaceId(workspaceId),
                baseCurrency(workspaceId),
                today(timeZone),
            ) { rules, currency, today ->
                // Rules carry an amount but no currency of their own — they are the workspace's, so
                // they are quoted in its base currency. A workspace row that has not arrived yet
                // leaves nothing to quote them in, and an invented currency would print the wrong
                // symbol beside a real amount.
                currency ?: return@combine emptyList()
                rules.upcomingOccurrences(today = today, currency = currency, limit = limit)
            }.distinctUntilChanged()
        }

    /**
     * The workspace base currency, live — observed rather than read once for the reason
     * [GenerateInsightsUseCase] documents: the session pointer is restored independently of the
     * `workspaces` row, so a latched `null` would leave the card empty for as long as the caller
     * stayed subscribed.
     */
    private fun baseCurrency(workspaceId: WorkspaceId): Flow<CurrencyCode?> =
        workspaceRepository.getAll()
            .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            .distinctUntilChanged()

    /**
     * Today's date, re-emitted when the calendar rolls over. Same shape and the same reason as
     * [GenerateInsightsUseCase.today]: nothing else in this chain moves at midnight, so a dashboard
     * left open overnight would keep calling yesterday "Today" — on the one widget whose whole job
     * is to say how close a payment is.
     */
    private fun today(timeZone: TimeZone): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            emit(today)
            val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            // Floored: a clock that jumps backwards must cost a minute of staleness, not a busy
            // loop re-dating every rule as fast as the dispatcher allows.
            delay((nextMidnight - now).coerceAtLeast(MIN_ROLLOVER_SLEEP))
        }
    }
}

/** Three rows is what the full-size card draws; the compact one keeps the first two. */
private const val UPCOMING_LIMIT = 3

/** See [GetUpcomingRecurringUseCase.today] — the floor that keeps a backwards clock jump cheap. */
private val MIN_ROLLOVER_SLEEP: Duration = 1.minutes
