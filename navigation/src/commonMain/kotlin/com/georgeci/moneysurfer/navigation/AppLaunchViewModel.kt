package com.georgeci.moneysurfer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
@Suppress("LongParameterList")
class AppLaunchViewModel(
    private val session: SessionPointers,
    private val syncCoordinator: SyncCoordinator,
    private val firstRunSeeder: FirstRunSeeder,
    private val syncFeatureFlag: SyncFeatureFlag,
    private val uiPreferences: UiPreferences,
    private val offlineBuildFlags: OfflineBuildFlags,
    private val getAccounts: GetAccountsUseCase,
) : ViewModel() {

    private val log = Logger.withTag(TAG)

    val targetRoute: StateFlow<Route?>
        field = MutableStateFlow<Route?>(null)

    init {
        viewModelScope.launch {
            // 0. Onboarding gates everything else (issue #173): nothing is seeded and no session
            //    state is inspected until the user has been introduced to the app. The splash
            //    therefore clears as soon as this one preference read returns.
            //    `OnboardingViewModel` runs the first-run seed itself when the user continues.
            if (!uiPreferences.onboardingCompleted.flow.first()) {
                targetRoute.value = Route.Onboarding
            } else {
                // 1. Returning user: give the first-run seed a chance to repair a half-finished
                //    install (offline build only — online binds a no-op), then decide the route.
                //    Failures (e.g. local DB hiccup) must not block startup — log and fall through
                //    so the app always reaches a navigable state.
                runCatching { firstRunSeeder.seedIfNeeded() }
                    .onFailure { log.w(it) { "[firstRun] seedIfNeeded threw — proceeding to route decision" } }

                // 2. One-shot startup decision based on the current snapshot.
                val userId = session.currentUserId.flow.first()
                val workspaceId = session.currentWorkspaceId.flow.first()
                targetRoute.value = resolveStartRoute(
                    userId = userId,
                    workspaceId = workspaceId,
                    isOffline = offlineBuildFlags.isOffline,
                    // Only the offline branch consults it, so don't query accounts otherwise.
                    hasAccounts = { getAccounts().first().isNotEmpty() },
                )
            }

            // 3. Only react to the user becoming null afterwards — drop the seed value first
            //    so the initial emission isn't double-counted.
            session.currentUserId.flow
                .drop(1)
                .filter { it == null }
                .collect { targetRoute.value = Route.SignIn }
        }

        // 4. Periodic sync while a Firebase-backed session is active. In-process loop
        //    instead of `BackgroundSyncScheduler.schedulePeriodic` because Android's
        //    WorkManager clamps periodic work to a 15-minute floor — we need true
        //    1-minute cadence here. `collectLatest` cancels the loop the moment the
        //    UID flips to null on logout.
        //    Gated by [SyncFeatureFlag]: when disabled, never start the ticker so the
        //    app makes zero Firestore reads/writes on the background path.
        if (syncFeatureFlag.enabled) {
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
        } else {
            log.i { "[sync] feature flag off — periodic ticker disabled" }
        }
    }

    private companion object {
        const val TAG = "AppLaunchVM"
        val PERIODIC_INTERVAL = 1.minutes
    }
}

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
