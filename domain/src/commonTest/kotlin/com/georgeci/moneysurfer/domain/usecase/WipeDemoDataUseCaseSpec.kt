package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

private class RecordingLocalDataReset : LocalDataResetRepository {
    var clearAllCount: Int = 0
    override suspend fun clearAll() {
        clearAllCount++
    }
}

class WipeDemoDataUseCaseSpec : StringSpec({

    "wipes Room and clears flag when hasUsedDemo is true" {
        runTest {
            val reset = RecordingLocalDataReset()
            val session = InMemorySessionPointers(hasUsedDemo = true)
            val useCase = WipeDemoDataUseCase(reset, session)

            useCase()

            reset.clearAllCount shouldBe 1
            session.hasUsedDemo.flow.first() shouldBe false
        }
    }

    "no-op when hasUsedDemo is false (real-account-only flow)" {
        runTest {
            val reset = RecordingLocalDataReset()
            val session = InMemorySessionPointers(hasUsedDemo = false)
            val useCase = WipeDemoDataUseCase(reset, session)

            useCase()

            reset.clearAllCount shouldBe 0
            session.hasUsedDemo.flow.first() shouldBe false
        }
    }

    "calling twice on a single demo session wipes once and stays cleared" {
        runTest {
            val reset = RecordingLocalDataReset()
            val session = InMemorySessionPointers(hasUsedDemo = true)
            val useCase = WipeDemoDataUseCase(reset, session)

            useCase()
            useCase()

            reset.clearAllCount shouldBe 1
            session.hasUsedDemo.flow.first() shouldBe false
        }
    }
})
