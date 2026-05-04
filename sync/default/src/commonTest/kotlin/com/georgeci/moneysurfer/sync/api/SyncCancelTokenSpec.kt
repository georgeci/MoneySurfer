package com.georgeci.moneysurfer.sync.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class SyncCancelTokenSpec : StringSpec({

    "SimpleCancelToken starts not cancelled" {
        SimpleCancelToken().isCancelled().shouldBeFalse()
    }

    "SimpleCancelToken cancel flips state" {
        val token = SimpleCancelToken()
        token.cancel()
        token.isCancelled().shouldBeTrue()
    }

    "SimpleCancelToken cancel is idempotent" {
        val token = SimpleCancelToken()
        token.cancel()
        token.cancel()
        token.isCancelled().shouldBeTrue()
    }

    "throwIfCancelled throws when cancelled" {
        val token = SimpleCancelToken()
        token.cancel()
        shouldThrow<SyncCancelledException> { token.throwIfCancelled() }
    }

    "throwIfCancelled does nothing when active" {
        val token = SimpleCancelToken()
        token.throwIfCancelled() // must not throw
    }

    // FAQ №2 — composite token uses `all` semantics

    "CompositeCancelToken not cancelled when no children cancelled" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        CompositeCancelToken(listOf(first, second)).isCancelled().shouldBeFalse()
    }

    "CompositeCancelToken not cancelled when only some children cancelled" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        val composite = CompositeCancelToken(listOf(first, second))
        first.cancel()
        composite.isCancelled().shouldBeFalse()
    }

    "CompositeCancelToken cancelled when all children cancelled" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        val composite = CompositeCancelToken(listOf(first, second))
        first.cancel()
        second.cancel()
        composite.isCancelled().shouldBeTrue()
    }

    "CompositeCancelToken cancel propagates to all children" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        val composite = CompositeCancelToken(listOf(first, second))
        composite.cancel()
        first.isCancelled().shouldBeTrue()
        second.isCancelled().shouldBeTrue()
        composite.isCancelled().shouldBeTrue()
    }

    "CompositeCancelToken with empty children treats as cancelled" {
        // `all { ... }` over empty list returns true. Acceptable — an empty
        // composite represents "no children to keep alive".
        CompositeCancelToken(emptyList()).isCancelled().shouldBeTrue()
    }

    "CompositeCancelToken throwIfCancelled when partial does not throw" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        val composite = CompositeCancelToken(listOf(first, second))
        first.cancel()
        composite.throwIfCancelled() // must not throw — second is still alive
        first.isCancelled().shouldBeTrue()
        second.isCancelled().shouldBeFalse()
    }

    "CompositeCancelToken throwIfCancelled when all cancelled throws" {
        val first = SimpleCancelToken()
        val second = SimpleCancelToken()
        val composite = CompositeCancelToken(listOf(first, second))
        first.cancel()
        second.cancel()
        shouldThrow<SyncCancelledException> { composite.throwIfCancelled() }
    }
})
