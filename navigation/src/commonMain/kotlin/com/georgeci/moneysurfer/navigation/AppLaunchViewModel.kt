package com.georgeci.moneysurfer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.config.ConfigHydration
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.config.SyncSettings
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.minutes

/**
 * Decides which route the app should land on at startup. This is **not** a reactive router —
 * once the initial decision is made, individual screens are responsible for posting their own
 * navigation side effects (e.g. SignInViewModel posts `NavigateToWorkspaceSelector` when its
 * auth flow finishes).
 *
 * The one exception is the user pointer flipping back to null (logout, session revoked, account
 * deleted) — we bounce to SignIn defensively, since no other screen owns "the user just
 * disappeared" navigation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
@Suppress("LongParameterList")
class AppLaunchViewModel(
    private val session: SessionPointers,
    private val syncCoordinator: SyncCoordinator,
    private val firstRunSeeder: FirstRunSeeder,
    private val syncSettings: SyncSettings,
    private val uiPreferences: UiPreferences,
    private val hostCapabilities: HostCapabilities,
    private val getAccounts: GetAccountsUseCase,
    private val configHydration: ConfigHydration,
) : ViewModel() {

    private val log = Logger.withTag(TAG)

    val targetRoute: StateFlow<Route?>
        field = MutableStateFlow<Route?>(null)

    init {
        viewModelScope.launch {
            // 0. Warm the configuration layers before anything reads them. `HostCapabilities` is
            //    synchronous, so hydration has to complete before the route decision below — and
            //    therefore before any screen that injects a host fact can be constructed.
            configHydration.hydrate()

            // 1. Onboarding gates everything else (issue #173): nothing is seeded and no session
            //    state is inspected until the user has been introduced to the app. The splash
            //    therefore clears as soon as this one preference read returns.
            //    `OnboardingViewModel` runs the first-run seed itself when the user continues.
            if (!uiPreferences.onboardingCompleted.flow.first()) {
                targetRoute.value = Route.Onboarding
            } else {
                // 2. Returning user: give the first-run seed a chance to repair a half-finished
                //    install (offline build only — online binds a no-op), then decide the route.
                //    Failures (e.g. local DB hiccup) must not block startup — log and fall through
                //    so the app always reaches a navigable state.
                runCatching { firstRunSeeder.seedIfNeeded() }
                    .onFailure { log.w(it) { "[firstRun] seedIfNeeded threw — proceeding to route decision" } }

                // 3. One-shot startup decision based on the current snapshot.
                val userId = session.currentUserId.first()
                val workspaceId = session.currentWorkspaceId.first()
                targetRoute.value = resolveStartRoute(
                    userId = userId,
                    workspaceId = workspaceId,
                    isOffline = hostCapabilities.isOffline,
                    // Only the offline branch consults it, so don't query accounts otherwise.
                    hasAccounts = { getAccounts().first().isNotEmpty() },
                )
            }

            // 4. Only react to the user becoming null afterwards — drop the seed value first
            //    so the initial emission isn't double-counted.
            session.currentUserId
                .drop(1)
                .filter { it == null }
                .collect { targetRoute.value = Route.SignIn }
        }

        startPeriodicSyncTicker()
    }

    /**
     * Periodic sync while a Firebase-backed session is active. In-process loop instead of
     * `BackgroundSyncScheduler.schedulePeriodic` because Android's WorkManager clamps periodic work
     * to a 15-minute floor — we need true 1-minute cadence here.
     *
     * `collectLatest` is what makes the gate work in both directions: a new emission from
     * [periodicSyncUids] — or the flow going quiet because sync was disabled — cancels the running
     * loop. In-flight work started by an earlier `requestSync` is left alone; the kill switch stops
     * new operations, it does not abort one mid-flight.
     */
    private fun startPeriodicSyncTicker() {
        viewModelScope.launch {
            periodicSyncUids(
                isSyncEnabled = syncSettings.isEnabled.onEach { enabled ->
                    if (!enabled) log.i { "[sync] disabled — periodic ticker not running" }
                },
                firebaseUid = session.currentFirebaseUid,
            ).collectLatest {
                log.i { "starting in-process sync ticker, interval=$PERIODIC_INTERVAL" }
                while (isActive) {
                    delay(PERIODIC_INTERVAL)
                    syncCoordinator.requestSync(SyncReason.BACKGROUND)
                }
            }
        }
    }

    private companion object {
        const val TAG = "AppLaunchVM"
        val PERIODIC_INTERVAL = 1.minutes
    }
}

/**
 * Which uid the background ticker should be running for, if any — the gate on its own, so it can be
 * tested without a view model scope or a one-minute clock.
 *
 * Emits nothing while sync is disabled, which is what keeps the app at zero Firestore reads on the
 * background path: the uid flow is not even subscribed. `flatMapLatest` rather than a one-shot read,
 * because a server-side kill switch can flip either way mid-session — and switching to an empty flow
 * is what cancels a ticker that was already running.
 *
 * A signed-out or demo session has no Firebase uid, so those are filtered out here rather than
 * checked again inside the loop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun periodicSyncUids(
    isSyncEnabled: Flow<Boolean>,
    firebaseUid: Flow<String?>,
): Flow<String> = isSyncEnabled
    .distinctUntilChanged()
    .flatMapLatest { enabled -> if (enabled) firebaseUid.distinctUntilChanged() else emptyFlow() }
    .filterNot { uid -> uid.isNullOrEmpty() }
    .filterNotNull()

/**
 * Start route for a user who has already been through the onboarding — the policy on its own, so
 * it can be tested without a view model scope. Onboarding itself is decided before this is called.
 *
 * The offline "no accounts yet" branch covers a process death on the first-run account screen:
 * the offline seed pins a workspace but no longer inserts a placeholder account, so an empty
 * workspace means the user never finished creating their first one.
 */
internal suspend fun resolveStartRoute(
    userId: UserId?,
    workspaceId: WorkspaceId?,
    isOffline: Boolean,
    hasAccounts: suspend () -> Boolean,
): Route = when {
    userId == null -> Route.SignIn
    workspaceId == null -> Route.WorkspaceSelector()
    isOffline && !hasAccounts() -> Route.AccountCreation(firstRun = true)
    else -> Route.Dashboard
}
