package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.GoalContributionDao
import com.georgeci.moneysurfer.data.db.dao.GoalDao
import com.georgeci.moneysurfer.data.db.entity.GoalContributionEntity
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.GoalContributionId
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuerImpl
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private const val WORKSPACE = "ws-1"
private val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000L)
private val TODAY = LocalDate.parse("2026-07-22")

private class FakeGoalContributionDao : GoalContributionDao {
    val rows: MutableList<GoalContributionEntity> = mutableListOf()

    private fun flowOf(rows: List<GoalContributionEntity>) =
        MutableStateFlow(rows).asStateFlow()

    override fun getByWorkspaceId(workspaceId: String): Flow<List<GoalContributionEntity>> =
        flowOf(rows.filter { it.workspaceId == workspaceId })

    override fun getByGoalId(goalId: String): Flow<List<GoalContributionEntity>> =
        flowOf(rows.filter { it.goalId == goalId }.sortedByDescending { it.occurredOn })

    override suspend fun listByGoalId(goalId: String): List<GoalContributionEntity> =
        rows.filter { it.goalId == goalId }

    override fun observeTotalByGoalId(goalId: String): Flow<Long> =
        MutableStateFlow(rows.toList()).asStateFlow()
            .map { all -> all.filter { it.goalId == goalId }.sumOf { it.amount } }

    override suspend fun totalByGoalId(goalId: String): Long =
        rows.filter { it.goalId == goalId }.sumOf { it.amount }

    override suspend fun getById(id: String): GoalContributionEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun insert(entity: GoalContributionEntity) { rows += entity }

    override suspend fun update(entity: GoalContributionEntity) {
        val idx = rows.indexOfFirst { it.id == entity.id }
        if (idx >= 0) rows[idx] = entity
    }

    override suspend fun upsertAll(entities: List<GoalContributionEntity>) {
        entities.forEach { entity ->
            val idx = rows.indexOfFirst { it.id == entity.id }
            if (idx >= 0) rows[idx] = entity else rows += entity
        }
    }

    override suspend fun delete(id: String) { rows.removeAll { it.id == id } }
    override suspend fun deleteAll() { rows.clear() }
}

/**
 * Mirrors Room's `ON DELETE CASCADE`: deleting a goal drops its contribution rows.
 *
 * Backed by a [MutableStateFlow] rather than a plain list so the observing queries
 * re-emit after every write, the way Room's do — a snapshot flow would silently pass
 * tests for code that depends on seeing a goal disappear.
 */
private class FakeGoalDao(private val contributions: FakeGoalContributionDao) : GoalDao {
    private val state = MutableStateFlow<List<GoalEntity>>(emptyList())

    val rows: List<GoalEntity> get() = state.value

    override fun getAll(): Flow<List<GoalEntity>> = state

    override fun getByWorkspaceId(workspaceId: String): Flow<List<GoalEntity>> =
        state.map { all -> all.filter { it.workspaceId == workspaceId } }

    override suspend fun getById(id: String): GoalEntity? = state.value.firstOrNull { it.id == id }

    override fun observeById(id: String): Flow<GoalEntity?> =
        state.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun insert(entity: GoalEntity) {
        state.value = state.value + entity
    }

    override suspend fun update(entity: GoalEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun upsertAll(entities: List<GoalEntity>) {
        val byId = entities.associateBy { it.id }
        val updated = state.value.map { byId[it.id] ?: it }
        state.value = updated + entities.filterNot { new -> state.value.any { it.id == new.id } }
    }

    override suspend fun delete(id: String) {
        state.value = state.value.filterNot { it.id == id }
        contributions.rows.removeAll { it.goalId == id }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
        contributions.rows.clear()
    }
}

private class GoalFakePendingMutationQueue : PendingMutationQueue {
    val items: MutableList<PendingMutation> = mutableListOf()
    override suspend fun enqueue(mutation: PendingMutation) { items += mutation }
    override suspend fun pending(scope: SyncScope, limit: Int) = items.take(limit)
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) { items.removeAll { it.id in ids } }
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = MutableStateFlow(0).asStateFlow()
}

private class GoalFakeAppVersionGate(
    private val current: AppVersionStatus = AppVersionStatus.Supported,
) : AppVersionGate {
    override val status = MutableStateFlow<AppVersionStatus?>(current).asStateFlow()
    override suspend fun refresh(): AppVersionStatus = current
    override fun isSyncAllowed(): Boolean = current !is AppVersionStatus.Unsupported
}

private class GoalTestRig(uid: String?) {
    val contributionDao = FakeGoalContributionDao()
    val goalDao = FakeGoalDao(contributionDao)
    val queue = GoalFakePendingMutationQueue()

    private val enqueuer = OutboxEnqueuerImpl(
        outbox = queue,
        session = InMemorySessionPointers(currentFirebaseUid = uid),
        appVersionGate = GoalFakeAppVersionGate(),
    )

    val goals = SavingsGoalRepositoryImpl(
        goalDao,
        contributionDao,
        enqueuer,
        ClockUseCase(),
        TimeFormatter(),
    )
    val contributions = GoalContributionRepositoryImpl(
        contributionDao,
        enqueuer,
        ClockUseCase(),
        TimeFormatter(),
    )
}

private fun aGoal(id: String = "goal-1") = SavingsGoal(
    id = GoalId(id),
    workspaceId = WorkspaceId(WORKSPACE),
    title = "New bike",
    emoji = "🚲",
    hue = 210,
    target = Money.fromMinor(150_000),
    currencyCode = CurrencyCode("USD"),
    startDate = TODAY,
    targetDate = null,
    accountId = null,
    status = GoalStatus.ACTIVE,
    createdAt = NOW,
    updatedAt = NOW,
)

private fun aContribution(
    id: String = "gc-1",
    goalId: String = "goal-1",
    minor: Long = 5_000,
) = GoalContribution(
    id = GoalContributionId(id),
    workspaceId = WorkspaceId(WORKSPACE),
    goalId = GoalId(goalId),
    amount = Money.fromMinor(minor),
    occurredOn = TODAY,
    note = "Payday",
    transferId = null,
    createdAt = NOW,
    updatedAt = NOW,
)

class GoalRepositoryDualWriteSpec : StringSpec({

    "insert writes both DAO and outbox as INSERT when signed in" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")

            rig.goals.insert(aGoal())

            rig.goalDao.rows shouldHaveSize 1
            val mutation = rig.queue.items.single()
            mutation.entityType shouldBe SyncEntityTypes.GOAL
            mutation.entityId shouldBe "goal-1"
            mutation.scopeKey shouldBe WORKSPACE
            mutation.operation shouldBe MutationOperation.INSERT
        }
    }

    "insert in a demo session writes DAO but skips the outbox" {
        runTest {
            val rig = GoalTestRig(uid = null)

            rig.goals.insert(aGoal())

            rig.goalDao.rows shouldHaveSize 1
            rig.queue.items.shouldBeEmpty()
        }
    }

    "update preserves createdAt and enqueues UPDATE" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")
            rig.goals.insert(aGoal())
            rig.queue.items.clear()

            rig.goals.update(aGoal().copy(title = "Better bike", createdAt = NOW.plusMillis(9_999)))

            val row = rig.goalDao.rows.single()
            row.title shouldBe "Better bike"
            row.createdAt shouldBe NOW.toEpochMilliseconds()
            rig.queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }

    "deleting a goal enqueues a delete for the goal AND each cascaded contribution" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")
            rig.goals.insert(aGoal())
            rig.contributions.insert(aContribution(id = "gc-1"))
            rig.contributions.insert(aContribution(id = "gc-2", minor = -1_000))
            rig.queue.items.clear()

            rig.goals.delete(GoalId("goal-1"))

            // Room's cascade removed the local rows without a DAO call...
            rig.goalDao.rows.shouldBeEmpty()
            rig.contributionDao.rows.shouldBeEmpty()
            // ...so the outbox has to carry all three deletes, or Firestore keeps orphans.
            rig.queue.items shouldHaveSize 3
            rig.queue.items.map { it.entityId } shouldContainExactlyInAnyOrder
                listOf("gc-1", "gc-2", "goal-1")
            rig.queue.items.all { it.operation == MutationOperation.DELETE } shouldBe true
            rig.queue.items.filter { it.entityType == SyncEntityTypes.GOAL_CONTRIBUTION }
                .map { it.entityId } shouldContainExactlyInAnyOrder listOf("gc-1", "gc-2")
        }
    }

    "deleting a goal that does not exist is a no-op" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")

            rig.goals.delete(GoalId("ghost"))

            rig.queue.items.shouldBeEmpty()
        }
    }

    "contribution insert dual-writes under its own entity type" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")

            rig.contributions.insert(aContribution())

            rig.contributionDao.rows shouldHaveSize 1
            val mutation = rig.queue.items.single()
            mutation.entityType shouldBe SyncEntityTypes.GOAL_CONTRIBUTION
            mutation.entityId shouldBe "gc-1"
            mutation.scopeKey shouldBe WORKSPACE
            mutation.operation shouldBe MutationOperation.INSERT
        }
    }

    "contribution delete enqueues a DELETE" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")
            rig.contributions.insert(aContribution())
            rig.queue.items.clear()

            rig.contributions.delete(GoalContributionId("gc-1"))

            rig.contributionDao.rows.shouldBeEmpty()
            rig.queue.items.single().operation shouldBe MutationOperation.DELETE
        }
    }

    "deleting a contribution that does not exist enqueues nothing" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")

            rig.contributions.delete(GoalContributionId("ghost"))

            rig.queue.items.shouldBeEmpty()
        }
    }

    "savedAmount is the signed sum of contributions" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")
            rig.contributions.insert(aContribution(id = "gc-1", minor = 5_000))
            rig.contributions.insert(aContribution(id = "gc-2", minor = -1_500))

            rig.contributions.savedAmount(GoalId("goal-1")) shouldBe Money.fromMinor(3_500)
        }
    }

    "savedAmount is zero for a goal with no contributions" {
        runTest {
            val rig = GoalTestRig(uid = "real-uid")

            rig.contributions.savedAmount(GoalId("goal-1")) shouldBe Money.zero()
        }
    }
})

private fun Instant.plusMillis(millis: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + millis)
