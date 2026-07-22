package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Startup routing policy for a user who is past the onboarding — [resolveStartRoute] is the half
 * of `AppLaunchViewModel` that has branches worth pinning down. Onboarding itself is decided
 * before this is reached (see `OnboardingViewModelTest` for what happens on the way out of it).
 */
class StartRouteTest : StringSpec({

    "no pinned user goes to sign-in" {
        resolveStartRoute(
            userId = null,
            workspaceId = WORKSPACE,
            isOffline = false,
            hasAccounts = { error("not consulted without a user") },
        ) shouldBe Route.SignIn
    }

    "a user without a workspace goes to the selector" {
        resolveStartRoute(
            userId = USER,
            workspaceId = null,
            isOffline = false,
            hasAccounts = { error("not consulted without a workspace") },
        ).shouldBeInstanceOf<Route.WorkspaceSelector>()
    }

    "an offline workspace with no accounts returns to the first-run account screen" {
        resolveStartRoute(
            userId = USER,
            workspaceId = WORKSPACE,
            isOffline = true,
            hasAccounts = { false },
        ) shouldBe Route.AccountCreation(firstRun = true)
    }

    "an offline workspace that already has an account goes to the dashboard" {
        resolveStartRoute(
            userId = USER,
            workspaceId = WORKSPACE,
            isOffline = true,
            hasAccounts = { true },
        ) shouldBe Route.Dashboard
    }

    "the online build never takes the first-run account branch — and never queries accounts" {
        resolveStartRoute(
            userId = USER,
            workspaceId = WORKSPACE,
            isOffline = false,
            hasAccounts = { error("online must not pay for an account query") },
        ) shouldBe Route.Dashboard
    }
})

private val USER = UserId("user-1")
private val WORKSPACE = WorkspaceId("ws-1")
