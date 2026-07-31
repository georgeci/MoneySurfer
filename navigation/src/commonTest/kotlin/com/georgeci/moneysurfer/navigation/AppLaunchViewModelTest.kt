package com.georgeci.moneysurfer.navigation

import androidx.lifecycle.ViewModelStore
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeSyncSettings
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRetentionRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.PurgeDeletedTransactionsUseCase
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.minutes

private val USER = UserId("u-1")
private val WORKSPACE = WorkspaceId("ws-1")

/**
 * Counts requests and then fails loudly. Every spec here runs with sync disabled, so the periodic
 * ticker must never reach the coordinator — a handle this fake could plausibly return would let
 * that regression pass as a silent extra sync instead.
 */
private class UnusedSyncCoordinator : SyncCoordinator {
    var requests: Int = 0
        private set

    override val state: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow(LastSyncOutcome.None)

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle {
        requests++
        error("the periodic ticker fired for $reason while sync was disabled")
    }

    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

private class StubAccountRepository(private val accounts: List<Account>) : AccountRepository {
    override fun getAll(): Flow<List<Account>> = flowOf(accounts)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getById(id: AccountId): Account? = accounts.firstOrNull { it.id == id }
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
}

@Suppress("LongParameterList")
private fun launchViewModel(
    session: InMemorySessionPointers = InMemorySessionPointers(),
    onboardingCompleted: Boolean = true,
    isOffline: Boolean = false,
    accounts: List<Account> = emptyList(),
    syncCoordinator: SyncCoordinator = UnusedSyncCoordinator(),
    syncEnabled: Boolean = false,
    seed: suspend () -> Unit = {},
    purged: Int = 0,
): AppLaunchViewModel =
    AppLaunchViewModel(
        session = session,
        syncCoordinator = syncCoordinator,
        firstRunSeeder = { seed() },
        syncSettings = FakeSyncSettings(enabled = syncEnabled),
        uiPreferences = FakeUiPreferences(onboardingCompleted = onboardingCompleted),
        hostCapabilities = FakeHostCapabilities(isOffline = isOffline),
        getAccounts = GetAccountsUseCase(StubAccountRepository(accounts), session),
        configHydration = { },
        remoteConfigRefresh = { },
        purgeDeletedTransactions = PurgeDeletedTransactionsUseCase(
            retention = TransactionRetentionRepository { purged },
            clock = ClockUseCase(),
        ),
    )

/**
 * The startup decision, end to end. `StartRouteTest` covers the routing rules on their own; what
 * this adds is the order the view model runs them in — and the two failures that must not keep the
 * app off its first screen.
 */
class AppLaunchViewModelTest : StringSpec({

    beforeTest { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterTest { Dispatchers.resetMain() }

    "a user who has not been onboarded lands on onboarding, whatever the session says" {
        runTest {
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
                onboardingCompleted = false,
            )

            viewModel.targetRoute.value shouldBe Route.Onboarding
        }
    }

    "a returning user with a workspace lands on the dashboard" {
        runTest {
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
            )

            viewModel.targetRoute.value shouldBe Route.Dashboard
        }
    }

    "a signed-out user lands on sign-in" {
        runTest {
            launchViewModel().targetRoute.value shouldBe Route.SignIn
        }
    }

    // A half-finished install must not be what keeps the app off its first screen.
    "a failing first-run seed is logged and the route decision still happens" {
        runTest {
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
                seed = { error("local database hiccup") },
            )

            viewModel.targetRoute.value shouldBe Route.Dashboard
        }
    }

    // Nothing else owns "the user just disappeared" navigation, so the launch view model watches
    // for it defensively — logout, a revoked session, a deleted account.
    "the user pointer going null bounces the app back to sign-in" {
        runTest {
            val session = InMemorySessionPointers(
                currentUserId = USER,
                currentWorkspaceId = WORKSPACE,
            )
            val viewModel = launchViewModel(session = session)
            viewModel.targetRoute.value shouldBe Route.Dashboard

            session.setCurrentUser(null)

            viewModel.targetRoute.value shouldBe Route.SignIn
        }
    }

    "the periodic ticker stays quiet while sync is disabled" {
        runTest {
            val coordinator = UnusedSyncCoordinator()
            launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentFirebaseUid = "fb-1",
                ),
                syncCoordinator = coordinator,
                syncEnabled = false,
            )

            testScheduler.advanceTimeBy(5.minutes)

            coordinator.requests shouldBe 0
        }
    }

    "a refresh of the remote config does not block the splash" {
        runTest {
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
            )

            viewModel.refreshRemoteConfig()

            viewModel.targetRoute.value shouldBe Route.Dashboard
        }
    }

    // An offline install whose first-run seed pinned a workspace but never finished the first
    // account: the user is sent back to finish it rather than to an empty dashboard.
    "an offline user with a workspace but no accounts resumes first-run account creation" {
        runTest {
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
                isOffline = true,
                accounts = emptyList(),
            )

            viewModel.targetRoute.value shouldBe Route.AccountCreation(firstRun = true)
        }
    }

    "clearing the view model tears the startup work down without changing the decision" {
        runTest {
            val store = ViewModelStore()
            val viewModel = launchViewModel(
                session = InMemorySessionPointers(
                    currentUserId = USER,
                    currentWorkspaceId = WORKSPACE,
                ),
            )
            store.put("launch", viewModel)

            store.clear()

            viewModel.targetRoute.value shouldBe Route.Dashboard
        }
    }

    // A rotation rebuilds the host's composition while this view model survives, so an unconsumed
    // decision would be re-applied on top of the restored back stack — which is how a user who had
    // signed in was sent back to onboarding by one rotation.
    "the launch decision is cleared once the host has applied it" {
        runTest {
            val viewModel = launchViewModel(onboardingCompleted = false)
            viewModel.targetRoute.value shouldBe Route.Onboarding
            viewModel.launchRouteApplied.value shouldBe false

            viewModel.onRouteApplied(Route.Onboarding)

            viewModel.targetRoute.value shouldBe null
            // The splash gate stays down across the rotation the cleared route no longer survives.
            viewModel.launchRouteApplied.value shouldBe true
        }
    }

    "the logout bounce still reaches the host after the launch decision was applied" {
        runTest {
            val session = InMemorySessionPointers(
                currentUserId = USER,
                currentWorkspaceId = WORKSPACE,
            )
            val viewModel = launchViewModel(session = session)
            viewModel.onRouteApplied(Route.Dashboard)
            viewModel.targetRoute.value shouldBe null

            session.setCurrentUser(null)

            viewModel.targetRoute.value shouldBe Route.SignIn
        }
    }

    // Applying a stale route must not swallow a decision the host has not seen yet.
    "a route applied late does not clear a newer decision" {
        runTest {
            val session = InMemorySessionPointers(
                currentUserId = USER,
                currentWorkspaceId = WORKSPACE,
            )
            val viewModel = launchViewModel(session = session)
            session.setCurrentUser(null)
            viewModel.targetRoute.value shouldBe Route.SignIn

            viewModel.onRouteApplied(Route.Dashboard)

            viewModel.targetRoute.value shouldBe Route.SignIn
        }
    }
})
