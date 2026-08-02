package com.georgeci.moneysurfer.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.georgeci.moneysurfer.navigation.util.rememberBackNavigationNavEntryDecorator
import com.georgeci.moneysurfer.navigation.util.rememberViewModelStoreNavEntryDecorator
import com.georgeci.moneysurfer.uikit.components.SurferSplash
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import io.github.irgaly.navigation3.resultstate.rememberNavigationResultNavEntryDecorator
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Polymorphic registry for every [Route] that can appear in a saved back stack. Missing an entry
 * here silently breaks saved-state restoration for that route (serializer-not-found at restore
 * time), so [com.georgeci.moneysurfer.navigation.RouteSerializerRegistryTest] guards it against
 * drift from [Route].
 */
internal val navKeySerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Route.Onboarding::class, Route.Onboarding.serializer())
        subclass(Route.SignIn::class, Route.SignIn.serializer())
        subclass(Route.Legal::class, Route.Legal.serializer())
        subclass(Route.WorkspaceSelector::class, Route.WorkspaceSelector.serializer())
        subclass(Route.WorkspaceCreation::class, Route.WorkspaceCreation.serializer())
        subclass(Route.WorkspaceMembers::class, Route.WorkspaceMembers.serializer())
        subclass(Route.WorkspaceManage::class, Route.WorkspaceManage.serializer())
        subclass(Route.WorkspaceInvite::class, Route.WorkspaceInvite.serializer())
        subclass(Route.WorkspaceMemberActions::class, Route.WorkspaceMemberActions.serializer())
        subclass(Route.IncomingInvites::class, Route.IncomingInvites.serializer())
        subclass(Route.Dashboard::class, Route.Dashboard.serializer())
        subclass(Route.DashboardCustomize::class, Route.DashboardCustomize.serializer())
        subclass(Route.AccountCreation::class, Route.AccountCreation.serializer())
        subclass(Route.AccountsManage::class, Route.AccountsManage.serializer())
        subclass(Route.CategoryCreation::class, Route.CategoryCreation.serializer())
        subclass(Route.CategoriesManage::class, Route.CategoriesManage.serializer())
        subclass(Route.CategoryDetails::class, Route.CategoryDetails.serializer())
        subclass(Route.CategoryChooser::class, Route.CategoryChooser.serializer())
        subclass(Route.Insights::class, Route.Insights.serializer())
        subclass(Route.Budgets::class, Route.Budgets.serializer())
        subclass(Route.BudgetDetails::class, Route.BudgetDetails.serializer())
        subclass(Route.BudgetCreation::class, Route.BudgetCreation.serializer())
        subclass(Route.AccountChooser::class, Route.AccountChooser.serializer())
        subclass(Route.TransactionsByAccount::class, Route.TransactionsByAccount.serializer())
        subclass(Route.TransactionCreation::class, Route.TransactionCreation.serializer())
        subclass(
            Route.AccountTransactionCreation::class,
            Route.AccountTransactionCreation.serializer(),
        )
        subclass(Route.TransactionFilters::class, Route.TransactionFilters.serializer())
        subclass(Route.AccountDetails::class, Route.AccountDetails.serializer())
        subclass(Route.TransactionDetails::class, Route.TransactionDetails.serializer())
        subclass(Route.Goals::class, Route.Goals.serializer())
        subclass(Route.GoalDetails::class, Route.GoalDetails.serializer())
        subclass(Route.GoalCreation::class, Route.GoalCreation.serializer())
        subclass(Route.GoalEdit::class, Route.GoalEdit.serializer())
        subclass(Route.GoalContribution::class, Route.GoalContribution.serializer())
        subclass(Route.Settings::class, Route.Settings.serializer())
        subclass(Route.SettingsAppearance::class, Route.SettingsAppearance.serializer())
        subclass(Route.SettingsPreferences::class, Route.SettingsPreferences.serializer())
        subclass(Route.SettingsSync::class, Route.SettingsSync.serializer())
        subclass(Route.SettingsBackup::class, Route.SettingsBackup.serializer())
        subclass(Route.SettingsCsv::class, Route.SettingsCsv.serializer())
        subclass(Route.SettingsAbout::class, Route.SettingsAbout.serializer())
        subclass(Route.SettingsLicenses::class, Route.SettingsLicenses.serializer())
        subclass(Route.SettingsDeleteAccount::class, Route.SettingsDeleteAccount.serializer())
        subclass(Route.SettingsDebugConfig::class, Route.SettingsDebugConfig.serializer())
        subclass(Route.SettingsDebugLog::class, Route.SettingsDebugLog.serializer())
    }
}

private val savedStateConfig = SavedStateConfiguration {
    serializersModule = navKeySerializersModule
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph(
    featureNavGraphs: List<FeatureNavGraph>,
) {
    val backStack = rememberNavBackStack(savedStateConfig, Route.SignIn)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val paneStrategy = rememberSurferPaneSceneStrategy<NavKey>()
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val appLaunchViewModel: AppLaunchViewModel = koinViewModel()
    val targetRoute by appLaunchViewModel.targetRoute.collectAsStateWithLifecycle()
    // The launch decision lands in a `LaunchedEffect`, i.e. one frame *after* the composition that
    // first sees a non-null `targetRoute`. Without this flag that frame renders the back stack's
    // bootstrap route (`Route.SignIn`) and `NavDisplay` then animates SignIn → Onboarding. It is
    // held by the view model, not this composition: a configuration change restores the back stack
    // but rebuilds the composition, and a flag that reset there would replay the splash.
    val launchRouteApplied by appLaunchViewModel.launchRouteApplied.collectAsStateWithLifecycle()
    LaunchedEffect(targetRoute) {
        navigator.applyLaunchRoute(targetRoute, backStack.lastOrNull(), appLaunchViewModel::onRouteApplied)
    }

    // Server-owned flags, pulled here rather than from the view model's `init` because this is the
    // only place in the app that has a lifecycle. `repeatOnLifecycle` runs the block on the first
    // composition and again on every return to the foreground — which is the whole propagation
    // model ADR-004 picked over a persistent snapshot listener. The refresh itself is fire-and-
    // forget inside the view model, so nothing here waits on Firestore.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            appLaunchViewModel.refreshRemoteConfig()
        }
    }

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        featureNavGraphs.forEach { featureNavGraph ->
            featureNavGraph(navigator)
        }
    }

    val currentTopLevel by remember(backStack) {
        derivedStateOf { backStack.lastOrNull { it is Route.TopLevel } as? Route.TopLevel }
    }

    val navDisplay: @Composable () -> Unit = {
        NavDisplay(
            modifier = Modifier.background(AppTheme.materialColors.background),
            backStack = backStack,
            onBack = { navigator.pop() },
            sceneStrategies = listOf(
                bottomSheetStrategy,
                paneStrategy,
            ),
            transitionSpec = {
                slideInHorizontally(tween(ANIMATION_DURATION)) { it } togetherWith
                    slideOutHorizontally(tween(ANIMATION_DURATION)) { -it }
            },
            popTransitionSpec = {
                slideInHorizontally(tween(ANIMATION_DURATION)) { -it } togetherWith
                    slideOutHorizontally(tween(ANIMATION_DURATION)) { it }
            },
            predictivePopTransitionSpec = { progress ->
                ContentTransform(
                    targetContentEnter = slideInHorizontally(tween(ANIMATION_DURATION)) { -it },
                    initialContentExit = slideOutHorizontally(tween(ANIMATION_DURATION)) { it },
                )
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                rememberBackNavigationNavEntryDecorator(backStack, entryProvider),
                rememberNavigationResultNavEntryDecorator(
                    backStack = backStack,
                    entryProvider = entryProvider,
                ),
            ),
            entryProvider = entryProvider,
        )
    }

    val snackbarHostState = rememberSnackbarHostState(koinInject())

    SyncStatusProvider {
        Scaffold(
            containerColor = AppTheme.materialColors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        ) { _ ->
            val topLevel = currentTopLevel
            if (!launchRouteApplied) {
                // Launch decision still pending, or made but not yet on the back stack — hold on
                // the splash instead of flashing the bootstrap route (`Route.SignIn`).
                SurferSplash()
            } else if (topLevel == null) {
                navDisplay()
            } else {
                AppNavigationShell(
                    currentTopLevel = topLevel,
                    onSelect = navigator::resetTo,
                    onOpenWorkspaceSelector = {
                        navigator.push(Route.WorkspaceSelector(showActions = true))
                    },
                    content = navDisplay,
                )
            }
        }
    }
}

/**
 * Puts a launch decision on the back stack and reports it back through [onApplied], which is what
 * stops the same decision from being replayed — the composition that calls this is rebuilt on every
 * configuration change while the view model behind [onApplied] is not.
 *
 * A null [route] is the "no decision yet" state, and also the state right after one was consumed:
 * both mean leave the back stack exactly as it is. That second case is the whole point on a
 * rotation, where the stack has just been restored and the decision that built it is long spent.
 *
 * Skipping the reset when [currentTop] already is [route] keeps that destination's entry — and the
 * view models scoped to it — alive, instead of tearing it down and rebuilding it for a decision
 * that lands where the user already is.
 *
 * Whole policy outside the composable, taking the two values it needs rather than reading them: the
 * navigation host has no test harness in this module, so anything left inside it is untested.
 */
internal fun AppNavigator.applyLaunchRoute(
    route: Route?,
    currentTop: NavKey?,
    onApplied: (Route) -> Unit,
) {
    if (route == null) {
        return
    }
    if (currentTop != route) {
        resetTo(route)
    }
    onApplied(route)
}

/** App-level [SnackbarHost] state that renders one-shot messages posted to [controller]. */
@Composable
private fun rememberSnackbarHostState(controller: SnackbarController): SnackbarHostState {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller) {
        controller.requests.collect { request ->
            hostState.currentSnackbarData?.dismiss()
            scope.launch { hostState.present(request) }
        }
    }
    return hostState
}

private suspend fun SnackbarHostState.present(request: SnackbarRequest) {
    val actionLabel = request.actionLabel?.let { getString(it) }
    val result = showSnackbar(
        message = request.resolveMessage(),
        actionLabel = actionLabel,
        withDismissAction = false,
        // Action snackbars (Undo) stay longer so the user can read and reach the button.
        duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) request.onAction?.invoke()
}

@Suppress("SpreadOperator") // messageArgs holds 0-1 items; the array copy cost is negligible.
private suspend fun SnackbarRequest.resolveMessage(): String =
    getString(message, *messageArgs.toTypedArray())

private const val ANIMATION_DURATION = 300
