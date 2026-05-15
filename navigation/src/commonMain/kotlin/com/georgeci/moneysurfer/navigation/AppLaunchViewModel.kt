package com.georgeci.moneysurfer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
@KoinViewModel
class AppLaunchViewModel(
    private val session: SessionPointers,
    private val syncCoordinator: SyncCoordinator,
    private val firstRunSeeder: FirstRunSeeder,
) : ViewModel() {

    private val log = Logger.withTag(TAG)

    private val _targetRoute = MutableStateFlow<Route?>(null)
    val targetRoute: StateFlow<Route?> = _targetRoute.asStateFlow()

    init {
        viewModelScope.launch {
            // 0. First-run hook (offline build only — online binds a no-op). Runs before the
            //    route decision so a fresh install can flip user + workspace pointers and land
            //    directly on the Dashboard instead of bouncing through SignIn / Selector.
            //    Failures (e.g. local DB hiccup) must not block startup — log and fall through
            //    to the route decision so the app always reaches a navigable state.
            runCatching { firstRunSeeder.seedIfNeeded() }
                .onFailure { log.w(it) { "[firstRun] seedIfNeeded threw — proceeding to route decision" } }

            // 1. One-shot startup decision based on the current snapshot.
            //    `currencyChosen` is `true` for every path except a fresh offline seed, which
            //    flips it false so the user picks a currency before landing on Dashboard.
            val userId = session.currentUserId.flow.first()
            val workspaceId = session.currentWorkspaceId.flow.first()
            val currencyChosen = session.currencyChosen.flow.first()
            _targetRoute.value = when {
                userId == null -> Route.SignIn
                workspaceId == null -> Route.WorkspaceSelector()
                !currencyChosen -> Route.FirstRunCurrency
                else -> Route.Dashboard
            }

            // 2. Only react to the user becoming null afterwards — drop the seed value first
            //    so the initial emission isn't double-counted.
            session.currentUserId.flow
                .drop(1)
                .filter { it == null }
                .collect { _targetRoute.value = Route.SignIn }
        }

        // 3. Periodic sync while a Firebase-backed session is active. In-process loop
        //    instead of `BackgroundSyncScheduler.schedulePeriodic` because Android's
        //    WorkManager clamps periodic work to a 15-minute floor — we need true
        //    1-minute cadence here. `collectLatest` cancels the loop the moment the
        //    UID flips to null on logout.
        viewModelScope.launch {
            session.currentFirebaseUid.flow
                .distinctUntilChanged()
                .collectLatest { uid ->
                    if (uid.isNullOrEmpty()) return@collectLatest
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
