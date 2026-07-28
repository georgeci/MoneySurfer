package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Instant

/**
 * What the wide-window drawer's user footer names — [resolveShellIdentity] is the half of
 * `AppShellViewModel` with branches worth pinning down, the rest being a `combine` of four flows.
 *
 * The fallback chain matters because each rung covers a different session: a Firebase user who set
 * a display name, one who never did and is known only by email, and a demo or anonymous session
 * that has neither.
 */
class ShellIdentityTest : StringSpec({

    "a display name wins over the provider email" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = listOf(user(displayName = "Georgy")),
            workspaces = emptyList(),
            providerEmail = "georgy@example.com",
        ).userName shouldBe "Georgy"
    }

    "the provider email stands in when the local row has no name" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = listOf(user(displayName = null)),
            workspaces = emptyList(),
            providerEmail = "georgy@example.com",
        ).userName shouldBe "georgy@example.com"
    }

    "a blank display name falls through to the email rather than rendering empty" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = listOf(user(displayName = "   ")),
            workspaces = emptyList(),
            providerEmail = "georgy@example.com",
        ).userName shouldBe "georgy@example.com"
    }

    "an anonymous session names nobody, and the drawer supplies the placeholder" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = listOf(user(displayName = null)),
            workspaces = emptyList(),
            providerEmail = null,
        ).userName shouldBe null
    }

    "a blank provider email is treated as absent" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = listOf(user(displayName = null)),
            workspaces = emptyList(),
            providerEmail = "",
        ).userName shouldBe null
    }

    "a user id with no matching row still falls back to the email" {
        // The pointer is written before the local row is guaranteed to exist on a fresh device.
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = emptyList(),
            workspaces = emptyList(),
            providerEmail = "georgy@example.com",
        ).userName shouldBe "georgy@example.com"
    }

    "the workspace name comes from the pinned workspace, not the first one" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = WORKSPACE,
            users = emptyList(),
            workspaces = listOf(
                workspace(id = WorkspaceId("other"), name = "Lisbon trip"),
                workspace(id = WORKSPACE, name = "Household budget"),
            ),
            providerEmail = null,
        ).workspaceName shouldBe "Household budget"
    }

    "no workspace is pinned yet" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = null,
            users = emptyList(),
            workspaces = listOf(workspace(id = WORKSPACE, name = "Household budget")),
            providerEmail = null,
        ).workspaceName shouldBe null
    }

    "a pinned workspace that is not in the list yet leaves the line blank" {
        resolveShellIdentity(
            userId = USER,
            workspaceId = WORKSPACE,
            users = emptyList(),
            workspaces = emptyList(),
            providerEmail = null,
        ).workspaceName shouldBe null
    }

    "a signed-out session names neither line" {
        resolveShellIdentity(
            userId = null,
            workspaceId = null,
            users = listOf(user(displayName = "Georgy")),
            workspaces = listOf(workspace(id = WORKSPACE, name = "Household budget")),
            providerEmail = null,
        ) shouldBe AppShellIdentity()
    }
})

private val USER = UserId("user-1")
private val WORKSPACE = WorkspaceId("ws-1")

private fun user(displayName: String?) = User(
    id = USER,
    displayName = displayName,
    email = null,
    isAnon = false,
)

private fun workspace(id: WorkspaceId, name: String) = Workspace(
    id = id,
    name = name,
    description = "",
    baseCurrency = CurrencyCode("EUR"),
    ownerId = USER,
    createdAt = Instant.fromEpochMilliseconds(0),
)
