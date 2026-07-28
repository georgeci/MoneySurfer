package com.georgeci.moneysurfer.domain.primitives

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Shared id of the sibling rows one receipt was split across — the same grouping technique
 * [TransferId] uses, and deliberately not a foreign key to a child table: every leg stays a
 * self-contained transaction, which is what keeps per-entity LWW sync correct for it.
 */
@JvmInline
@Serializable
value class SplitId(val value: String) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun uuid(): SplitId = SplitId(Uuid.random().toString())
    }
}
