package com.georgeci.moneysurfer.data.sync

import arrow.core.raise.either
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.sync.UploadPendingChangesUseCase
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.domain.sync.UploadSummary
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.plugin.SyncEntityPlugin
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import org.koin.core.annotation.Single
import kotlin.coroutines.cancellation.CancellationException

/**
 * Drains the outbox by dispatching each [PendingMutation] to the matching
 * [SyncEntityPlugin]. Plugins are discovered via Koin `getAll<SyncEntityPlugin>()`.
 *
 * Successfully pushed mutations are removed; failures bump `attempts` and
 * return to `PENDING` for the next sync cycle.
 */
@Single(binds = [UploadPendingChangesUseCase::class])
class UploadPendingChangesUseCaseImpl(
    private val outbox: PendingMutationQueue,
    private val authInfo: CurrentAuthInfo,
    private val plugins: List<SyncEntityPlugin>,
) : UploadPendingChangesUseCase {

    private val log = Logger.withTag(TAG)

    override suspend fun invoke(
        scope: SyncScope,
        onProgress: suspend (UploadProgress) -> Unit,
        cancelToken: SyncCancelToken,
    ): SyncResult<UploadSummary> = either {
        val batch = outbox.pending(scope, limit = PendingMutationQueue.DEFAULT_BATCH_LIMIT)
        if (batch.isEmpty()) {
            log.d { "[drain] scope=$scope batch=empty" }
            return@either UploadSummary(uploadedCount = 0)
        }
        log.i {
            "[drain] scope=$scope batch=${batch.size} types=" +
                batch.groupingBy { it.entityType }.eachCount()
        }

        outbox.markInFlight(batch.map { it.id })

        val completed = mutableListOf<String>()
        val allIds = batch.map { it.id }

        try {
            batch.forEachIndexed { index, mutation ->
                cancelToken.throwIfCancelled()
                val plugin = plugins.find { it.entityType == mutation.entityType }
                if (plugin != null) {
                    plugin.push(mutation)
                } else {
                    log.w { "[drain:skip] no plugin for entityType=${mutation.entityType} id=${mutation.id}" }
                }
                completed += mutation.id
                onProgress(
                    UploadProgress(
                        entityType = mutation.entityType,
                        current = index + 1,
                        total = batch.size,
                    ),
                )
            }
            outbox.markCompleted(completed)
        } catch (cancelled: CancellationException) {
            outbox.markCompleted(completed)
            (allIds - completed.toSet()).forEach { id -> outbox.markFailed(id, "cancelled") }
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught")
            failure: Throwable,
        ) {
            val failed = batch.getOrNull(completed.size)
            log.e(failure) {
                buildString {
                    append("[drain:fail] uploaded=${completed.size}/${batch.size} ")
                    append("reason=${failure.message}")
                    append(" auth=[${authInfo.diagnostics()}]")
                    if (failed != null) {
                        append(" failedMutation=[")
                        append("id=${failed.id}")
                        append(" type=${failed.entityType}")
                        append(" entity=${failed.entityId}")
                        append(" scope=${failed.scopeKey ?: "<none>"}")
                        append(" op=${failed.operation}")
                        append("]")
                    }
                }
            }
            outbox.markCompleted(completed)
            val pending = allIds - completed.toSet()
            pending.forEach { id -> outbox.markFailed(id, failure.message ?: "unknown") }
            raise(failure.toSyncError())
        }

        log.i { "[drain:done] uploaded=${completed.size}" }
        UploadSummary(uploadedCount = completed.size)
    }

    private companion object {
        const val TAG = "UploadPending"
    }
}
