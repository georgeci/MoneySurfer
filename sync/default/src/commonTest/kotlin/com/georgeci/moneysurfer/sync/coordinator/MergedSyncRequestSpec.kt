package com.georgeci.moneysurfer.sync.coordinator

import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.internal.coordinator.MergedSyncRequest
import com.georgeci.moneysurfer.sync.internal.coordinator.SyncRequest
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class MergedSyncRequestSpec : StringSpec({

    "from wraps single request as merged with one child" {
        val request = SyncRequest(id = SyncRequestId("a"), reason = SyncReason.MANUAL)
        val merged = MergedSyncRequest.from(request)

        merged.id shouldBe request.id
        merged.reasons shouldBe setOf(SyncReason.MANUAL)
        merged.scope shouldBe SyncScope.AllUserData // MANUAL → AllUserData
        merged.childRequests shouldContainExactly listOf(request)
    }

    "merge appends child and unions reasons" {
        val first = SyncRequest(id = SyncRequestId("a"), reason = SyncReason.LOCAL_CHANGE)
        val second = SyncRequest(id = SyncRequestId("b"), reason = SyncReason.MANUAL)
        val merged = MergedSyncRequest.from(first).merge(second)

        merged.id shouldBe first.id // id stays from the first request
        merged.reasons shouldBe setOf(SyncReason.LOCAL_CHANGE, SyncReason.MANUAL)
        merged.childRequests shouldContainExactly listOf(first, second)
        // mergeScope: UploadOnly + AllUserData → AllUserData (widest wins)
        merged.scope shouldBe SyncScope.AllUserData
    }

    "merge keeps narrower scope when both narrow" {
        val first = SyncRequest(id = SyncRequestId("a"), reason = SyncReason.LOCAL_CHANGE)
        val second = SyncRequest(id = SyncRequestId("b"), reason = SyncReason.LOCAL_CHANGE)
        val merged = MergedSyncRequest.from(first).merge(second)

        merged.scope shouldBe SyncScope.UploadOnly
        merged.reasons shouldBe setOf(SyncReason.LOCAL_CHANGE)
    }
})
