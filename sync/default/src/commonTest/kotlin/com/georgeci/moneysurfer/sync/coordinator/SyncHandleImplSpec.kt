package com.georgeci.moneysurfer.sync.coordinator

import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.internal.coordinator.SyncHandleImpl
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class SyncHandleImplSpec : StringSpec({

    "cancel invokes the supplied cancelAction once" {
        var calls = 0
        val handle = SyncHandleImpl(
            id = SyncRequestId("h-1"),
            status = MutableStateFlow<SyncHandleStatus>(SyncHandleStatus.Queued),
            steps = MutableSharedFlow<SyncStep>(
                replay = 1,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            ),
            result = CompletableDeferred<SyncResult<SyncSummary>>(),
            cancelAction = { calls++ },
        )

        handle.cancel()
        handle.cancel()

        calls shouldBe 2
    }

    "exposes id, status, steps, result via the SyncHandle interface" {
        val id = SyncRequestId("h-2")
        val status = MutableStateFlow<SyncHandleStatus>(SyncHandleStatus.Queued)
        val steps = MutableSharedFlow<SyncStep>(replay = 1)
        val result = CompletableDeferred<SyncResult<SyncSummary>>()
        val handle: SyncHandle = SyncHandleImpl(
            id = id,
            status = status,
            steps = steps,
            result = result,
            cancelAction = {},
        )

        handle.id shouldBe id
        handle.status shouldBe status
        handle.steps shouldBe steps
        handle.result shouldBe result
        result.isCompleted.shouldBeFalse() // freshly built — must not be completed yet
    }
})
