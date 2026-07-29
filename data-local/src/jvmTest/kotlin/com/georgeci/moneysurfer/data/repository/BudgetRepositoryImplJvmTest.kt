package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours

/**
 * Round-trips budgets through the real bundled SQLite, because the mapping the repository owns is
 * lossy in the places that matter: the category list is a CSV column, the period is stored by
 * name, and the dates are text. A fake DAO would hand back whatever the mapper just produced.
 */
class BudgetRepositoryImplJvmTest : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var outbox: RecordingOutbox
    lateinit var repository: BudgetRepositoryImpl

    val later = testInstant + 3.hours

    beforeEach {
        database = inMemoryLocalDatabase()
        outbox = RecordingOutbox()
        repository = BudgetRepositoryImpl(
            dao = database.budgetDao(),
            outboxEnqueuer = outbox,
            clock = ClockUseCase(FixedClock(later)),
            timeFormatter = TimeFormatter(),
        )
        database.seedWorkspaceRows()
    }

    afterEach { database.close() }

    "a stored budget reads back with every field intact" {
        val budget = aBudget(
            categoryIds = listOf(categoryId("c-1"), categoryId("c-2")),
            amount = 250.dollars,
            period = BudgetPeriod.WEEKLY,
            startDate = LocalDate(2025, 6, 1),
            rollover = true,
        )

        repository.insert(budget)

        repository.getById(budget.id) shouldBe budget
    }

    "a budget with no categories means every expense category, not a phantom one" {
        repository.insert(aBudget(categoryIds = emptyList()))

        repository.getById(budgetId())?.categoryIds shouldBe emptyList()
    }

    "an unknown period on the row falls back to monthly rather than failing the read" {
        repository.insert(aBudget(period = BudgetPeriod.MONTHLY))
        val stored = database.budgetDao().getById(budgetId().value)!!
        database.budgetDao().update(stored.copy(period = "FORTNIGHTLY"))

        repository.getById(budgetId())?.period shouldBe BudgetPeriod.MONTHLY
    }

    "an insert is paired with an INSERT outbox entry scoped to the workspace" {
        repository.insert(aBudget())

        outbox.records shouldContainExactly listOf(
            OutboxRecord(
                entityType = SyncEntityTypes.BUDGET,
                entityId = budgetId().value,
                scopeKey = TEST_WORKSPACE_ID,
                operation = MutationOperation.INSERT,
            ),
        )
    }

    // The creation moment belongs to the row, not to the object the caller happens to be holding —
    // an edit form that round-tripped a stale `createdAt` would otherwise rewrite history.
    "an update keeps the stored createdAt and stamps updatedAt from the clock" {
        repository.insert(aBudget(createdAt = testInstant))

        repository.update(aBudget(name = "Renamed", createdAt = testInstant + 99.hours))

        val updated = repository.getById(budgetId())!!
        updated.name shouldBe "Renamed"
        updated.createdAt shouldBe testInstant
        updated.updatedAt shouldBe later
    }

    "setActive flips the flag, stamps the clock and enqueues an update" {
        repository.insert(aBudget(isActive = true))
        outbox.records.clear()

        repository.setActive(budgetId(), isActive = false)

        val updated = repository.getById(budgetId())!!
        updated.isActive shouldBe false
        updated.updatedAt shouldBe later
        outbox.records.map { it.operation } shouldContainExactly listOf(MutationOperation.UPDATE)
    }

    "setActive on a budget that is gone is a no-op rather than a failure" {
        repository.setActive(budgetId("missing"), isActive = false)

        outbox.records shouldContainExactly emptyList()
    }

    "a delete drops the row and enqueues a tombstone" {
        repository.insert(aBudget())
        outbox.records.clear()

        repository.delete(budgetId())

        repository.getById(budgetId()).shouldBeNull()
        outbox.records shouldContainExactly listOf(
            OutboxRecord(
                entityType = SyncEntityTypes.BUDGET,
                entityId = budgetId().value,
                scopeKey = TEST_WORKSPACE_ID,
                operation = null,
            ),
        )
    }

    // Nothing was deleted, so there is nothing for the other devices to delete either.
    "deleting a budget that is already gone enqueues nothing" {
        repository.delete(budgetId("missing"))

        outbox.records shouldContainExactly emptyList()
    }

    "the workspace stream carries the workspace's budgets and the global one carries all" {
        repository.insert(aBudget(id = budgetId("b-1")))
        repository.insert(aBudget(id = budgetId("b-2"), name = "Fuel"))

        repository.getByWorkspaceId(aBudget().workspaceId).first()
            .map { it.name } shouldContainExactlyInAnyOrder listOf("Groceries", "Fuel")
        repository.getAll().first().size shouldBe 2
    }
})
