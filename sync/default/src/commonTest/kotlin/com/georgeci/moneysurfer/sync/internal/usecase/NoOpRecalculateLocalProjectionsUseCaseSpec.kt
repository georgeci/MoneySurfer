package com.georgeci.moneysurfer.sync.internal.usecase

import com.georgeci.moneysurfer.domain.sync.ProjectionScope
import com.georgeci.moneysurfer.sync.api.SimpleCancelToken
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

class NoOpRecalculateLocalProjectionsUseCaseSpec : StringSpec({

    "returns ProjectionSummary with zero recalculations for All scope" {
        runTest {
            val result = NoOpRecalculateLocalProjectionsUseCase().invoke(
                scope = ProjectionScope.All,
                cancelToken = SimpleCancelToken(),
            )
            result.isRight() shouldBe true
            result.fold({ error("unexpected: $it") }, { it.recalculatedCount }) shouldBe 0
        }
    }

    "returns Right for a Workspace scope" {
        runTest {
            val result = NoOpRecalculateLocalProjectionsUseCase().invoke(
                scope = ProjectionScope.Workspace("ws-1"),
                cancelToken = SimpleCancelToken(),
            )
            result.isRight() shouldBe true
        }
    }

    "ignores a cancel token that is already cancelled (NoOp does no work)" {
        runTest {
            val token = SimpleCancelToken().apply { cancel() }
            val result = NoOpRecalculateLocalProjectionsUseCase().invoke(ProjectionScope.All, token)
            result.isRight() shouldBe true
        }
    }
})
