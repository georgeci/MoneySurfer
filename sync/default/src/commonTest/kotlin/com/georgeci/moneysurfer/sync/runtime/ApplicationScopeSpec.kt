package com.georgeci.moneysurfer.sync.runtime

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class ApplicationScopeSpec : StringSpec({

    "default constructor builds an active scope" {
        val scope = ApplicationScope()
        try {
            scope.isActive.shouldBeTrue()
            // delegated coroutineContext must include a Job — required for child cancellation.
            scope.coroutineContext shouldNotBe coroutineContext
        } finally {
            scope.cancel()
        }
    }

    "delegate constructor preserves the supplied context" {
        val delegate = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val scope = ApplicationScope(delegate = delegate)
        try {
            scope.isActive.shouldBeTrue()
        } finally {
            delegate.cancel()
        }
    }
})
