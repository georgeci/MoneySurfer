package com.georgeci.moneysurfer.domain.usecase

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * The workspace the session points at. Every "cannot name it" case answers null — the callers can
 * only render one thing for all of them — and the answer has to keep moving after the first
 * emission, which is the whole reason this observes the rows rather than reading them once.
 */
class GetCurrentWorkspaceUseCaseTest : StringSpec({

    val ws = workspaceId("ws-1")

    "resolves the row the session points at" {
        val useCase = GetCurrentWorkspaceUseCase(
            workspaceRepository = FakeGoalWorkspaceRepository(
                listOf(
                    aWorkspace(id = workspaceId("ws-other"), name = "Other"),
                    aWorkspace(id = ws, name = "Household"),
                ),
            ),
            session = InMemorySessionPointers(currentWorkspaceId = ws),
        )

        useCase().first()?.name shouldBe "Household"
    }

    "answers null while nobody is signed in" {
        val useCase = GetCurrentWorkspaceUseCase(
            workspaceRepository = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws))),
            session = InMemorySessionPointers(currentWorkspaceId = null),
        )

        useCase().first() shouldBe null
    }

    "answers null while the pointed-at row has not been pulled yet" {
        val useCase = GetCurrentWorkspaceUseCase(
            workspaceRepository = FakeGoalWorkspaceRepository(emptyList()),
            session = InMemorySessionPointers(currentWorkspaceId = ws),
        )

        useCase().first() shouldBe null
    }

    "a rename reaches a live collector — nothing has to re-subscribe" {
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, name = "Household")))
        val useCase = GetCurrentWorkspaceUseCase(
            workspaceRepository = workspaces,
            session = InMemorySessionPointers(currentWorkspaceId = ws),
        )

        useCase().test {
            awaitItem()?.name shouldBe "Household"
            workspaces.update(aWorkspace(id = ws, name = "Household budget"))
            awaitItem()?.name shouldBe "Household budget"
            cancelAndIgnoreRemainingEvents()
        }
    }

    "switching workspace re-points the answer" {
        val other = workspaceId("ws-2")
        val session = InMemorySessionPointers(currentWorkspaceId = ws)
        val useCase = GetCurrentWorkspaceUseCase(
            workspaceRepository = FakeGoalWorkspaceRepository(
                listOf(aWorkspace(id = ws, name = "Household"), aWorkspace(id = other, name = "Freelance")),
            ),
            session = session,
        )

        useCase().test {
            awaitItem()?.name shouldBe "Household"
            session.setCurrentWorkspace(other)
            awaitItem()?.name shouldBe "Freelance"
            cancelAndIgnoreRemainingEvents()
        }
    }
})
