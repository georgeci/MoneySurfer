package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.repository.AccountRepositoryImpl
import com.georgeci.moneysurfer.data.repository.CategoryRepositoryImpl
import com.georgeci.moneysurfer.data.repository.TimeFormatter
import com.georgeci.moneysurfer.data.repository.TransactionRepositoryImpl
import com.georgeci.moneysurfer.data.repository.WorkspaceRepositoryImpl
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer

/**
 * Wires the real account/transaction/category data + domain layers around an
 * [IntegrationHarness] without pulling in Koin or the sync stack. The outbox is
 * a no-op — these tests exercise local persistence and balance math, not push.
 */
internal class FinanceStack(harness: IntegrationHarness) {
    val outbox: OutboxEnqueuer = NoOpOutbox
    val clock = ClockUseCase()
    val timeFormatter = TimeFormatter()

    val accountRepository = AccountRepositoryImpl(
        dao = harness.database.accountDao(),
        outboxEnqueuer = outbox,
        clock = clock,
        timeFormatter = timeFormatter,
    )
    val transactionRepository = TransactionRepositoryImpl(
        dao = harness.database.transactionDao(),
        outboxEnqueuer = outbox,
        clock = clock,
        timeFormatter = timeFormatter,
    )
    val categoryRepository = CategoryRepositoryImpl(
        dao = harness.database.categoryDao(),
        outboxEnqueuer = outbox,
        clock = clock,
        timeFormatter = timeFormatter,
    )

    val workspaceRepository = WorkspaceRepositoryImpl(
        dao = harness.database.workspaceDao(),
        outboxEnqueuer = outbox,
        clock = clock,
        timeFormatter = timeFormatter,
    )

    private val applyTransactionChange = ApplyTransactionChangeUseCase(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
    )
    val createTransaction = CreateTransactionUseCase(applyTransactionChange)
    val createTransfer = CreateTransferUseCase(
        categoryRepository = categoryRepository,
        applyTransactionChange = applyTransactionChange,
        getCurrentTime = GetCurrentTimeUseCase(clock),
    )
    val updateWorkspaceCurrency = UpdateWorkspaceCurrencyUseCase(
        workspaceRepository = workspaceRepository,
        accountRepository = accountRepository,
    )
}

internal const val OWNER_UID = "owner-uid"
internal const val WORKSPACE_ID_VALUE = "ws-1"
internal val DEFAULT_WORKSPACE_ID = WorkspaceId(WORKSPACE_ID_VALUE)
internal const val FIXTURE_TIME: Long = 1_700_000_000_000L

internal suspend fun IntegrationHarness.seedWorkspace() {
    database.userDao().insert(UserEntity(id = OWNER_UID, displayName = "Owner", isAnon = false))
    database.workspaceDao().insert(
        WorkspaceEntity(
            id = WORKSPACE_ID_VALUE,
            name = "WS",
            description = "",
            baseCurrency = "USD",
            ownerId = OWNER_UID,
            createdAt = FIXTURE_TIME,
            archived = false,
            updatedAt = FIXTURE_TIME,
        ),
    )
}

private object NoOpOutbox : OutboxEnqueuer {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) = Unit

    override suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        scopeKey: String?,
    ) = Unit

    override suspend fun isEnabled(): Boolean = false
}
