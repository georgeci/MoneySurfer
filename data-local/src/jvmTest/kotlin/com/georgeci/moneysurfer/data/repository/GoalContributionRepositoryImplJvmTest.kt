package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aGoalContribution
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.goalContributionId
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours

/**
 * A goal's saved amount is never stored — it is `SUM(contributions.amount)`, so these specs run
 * against real SQL rather than a fake that could only echo the arithmetic back.
 */
class GoalContributionRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var outbox: RecordingOutbox
    lateinit var repository: GoalContributionRepositoryImpl

    val later = testInstant + 2.hours

    beforeEach {
        database = inMemoryLocalDatabase()
        outbox = RecordingOutbox()
        repository = GoalContributionRepositoryImpl(
            dao = database.goalContributionDao(),
            outboxEnqueuer = outbox,
            clock = ClockUseCase(FixedClock(later)),
            timeFormatter = TimeFormatter(),
        )
        database.seedWorkspaceRows()
        database.seedGoalRow()
    }

    afterEach { database.close() }

    "a stored contribution reads back with every field intact" {
        val contribution = aGoalContribution(
            amount = 25.dollars,
            occurredOn = LocalDate(2025, 4, 7),
            note = "birthday money",
        )

        repository.insert(contribution)

        repository.getById(contribution.id) shouldBe contribution
    }

    // Signed sum: a withdrawal is a negative row, not a separate table.
    "the saved amount is the signed sum of the goal's rows" {
        repository.insert(aGoalContribution(id = goalContributionId("gc-1"), amount = 100.dollars))
        repository.insert(aGoalContribution(id = goalContributionId("gc-2"), amount = 40.dollars))
        repository.insert(aGoalContribution(id = goalContributionId("gc-3"), amount = (-15).dollars))

        repository.savedAmount(goalId()) shouldBe 125.dollars
        repository.observeSavedAmount(goalId()).first() shouldBe 125.dollars
    }

    "a goal with no contributions has saved nothing rather than no answer" {
        repository.savedAmount(goalId()) shouldBe Money.fromMinor(0)
        repository.observeSavedAmount(goalId()).first() shouldBe Money.fromMinor(0)
    }

    "an insert is paired with an INSERT outbox entry scoped to the workspace" {
        repository.insert(aGoalContribution())

        outbox.records shouldContainExactly listOf(
            OutboxRecord(
                entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
                entityId = goalContributionId().value,
                scopeKey = TEST_WORKSPACE_ID,
                operation = MutationOperation.INSERT,
            ),
        )
    }

    "an update keeps the stored createdAt and stamps updatedAt from the clock" {
        repository.insert(aGoalContribution(amount = 100.dollars))

        repository.update(aGoalContribution(amount = 60.dollars, createdAt = testInstant + 99.hours))

        val updated = repository.getById(goalContributionId())!!
        updated.amount shouldBe 60.dollars
        updated.createdAt shouldBe testInstant
        updated.updatedAt shouldBe later
    }

    "a delete drops the row and enqueues a tombstone" {
        repository.insert(aGoalContribution())
        outbox.records.clear()

        repository.delete(goalContributionId())

        repository.getById(goalContributionId()).shouldBeNull()
        outbox.records shouldContainExactly listOf(
            OutboxRecord(
                entityType = SyncEntityTypes.GOAL_CONTRIBUTION,
                entityId = goalContributionId().value,
                scopeKey = TEST_WORKSPACE_ID,
                operation = null,
            ),
        )
    }

    "deleting a contribution that is already gone enqueues nothing" {
        repository.delete(goalContributionId("missing"))

        outbox.records shouldContainExactly emptyList()
    }

    "the goal and workspace streams both carry the stored rows" {
        repository.insert(aGoalContribution(id = goalContributionId("gc-1")))
        repository.insert(aGoalContribution(id = goalContributionId("gc-2")))

        repository.getByGoalId(goalId()).first().size shouldBe 2
        repository.getByWorkspaceId(aGoalContribution().workspaceId).first().size shouldBe 2
    }
})
