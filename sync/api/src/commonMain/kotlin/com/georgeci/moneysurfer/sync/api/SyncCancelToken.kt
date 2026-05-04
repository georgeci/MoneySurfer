package com.georgeci.moneysurfer.sync.api

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cancellation token for sync operations.
 *
 * Usage: inside long-running steps (upload, pull) call `throwIfCancelled()`
 * between major sub-operations. See SyncCoordinatorFAQ.md №2.
 */
abstract class SyncCancelToken {
    abstract fun isCancelled(): Boolean
    abstract fun cancel()

    fun throwIfCancelled() {
        if (isCancelled()) throw SyncCancelledException()
    }
}

@OptIn(ExperimentalAtomicApi::class)
class SimpleCancelToken : SyncCancelToken() {
    private val flag = AtomicBoolean(false)
    override fun isCancelled(): Boolean = flag.load()
    override fun cancel() { flag.store(true) }
}

/**
 * Token wrapping a set of child tokens with `all` semantics:
 * considered cancelled only when ALL children are cancelled.
 * Required for merged sync requests — cancelling one child does not abort the rest.
 * See SyncCoordinatorFAQ.md №2, №4.
 */
class CompositeCancelToken(
    private val children: List<SyncCancelToken>,
) : SyncCancelToken() {
    override fun isCancelled(): Boolean = children.all { it.isCancelled() }
    override fun cancel() { children.forEach { it.cancel() } }
}

class SyncCancelledException : CancellationException("Sync cancelled")
