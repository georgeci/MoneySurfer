package com.georgeci.moneysurfer.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.georgeci.moneysurfer.navigation.util.rememberViewModelStoreNavEntryDecorator
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import io.github.irgaly.navigation3.resultstate.rememberNavigationResultNavEntryDecorator
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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
        subclass(Route.SignIn::class, Route.SignIn.serializer())
        subclass(Route.Legal::class, Route.Legal.serializer())
        subclass(Route.WorkspaceSelector::class, Route.WorkspaceSelector.serializer())
        subclass(Route.WorkspaceCreation::class, Route.WorkspaceCreation.serializer())
        subclass(Route.WorkspaceMembers::class, Route.WorkspaceMembers.serializer())
        subclass(Route.WorkspaceManage::class, Route.WorkspaceManage.serializer())
        subclass(Route.WorkspaceInvite::class, Route.WorkspaceInvite.serializer())
        subclass(Route.WorkspaceMemberActions::class, Route.WorkspaceMemberActions.serializer())
        subclass(Route.IncomingInvites::class, Route.IncomingInvites.serializer())
        subclass(Route.FirstRunCurrency::class, Route.FirstRunCurrency.serializer())
        subclass(Route.Dashboard::class, Route.Dashboard.serializer())
        subclass(Route.AccountCreation::class, Route.AccountCreation.serializer())
        subclass(Route.AccountsManage::class, Route.AccountsManage.serializer())
        subclass(Route.CategoryCreation::class, Route.CategoryCreation.serializer())
        subclass(Route.CategoriesManage::class, Route.CategoriesManage.serializer())
        subclass(Route.CategoryChooser::class, Route.CategoryChooser.serializer())
        subclass(Route.AccountChooser::class, Route.AccountChooser.serializer())
        subclass(Route.TransactionsByAccount::class, Route.TransactionsByAccount.serializer())
        subclass(Route.TransactionCreation::class, Route.TransactionCreation.serializer())
        subclass(Route.AccountDetails::class, Route.AccountDetails.serializer())
        subclass(Route.TransactionDetails::class, Route.TransactionDetails.serializer())
        subclass(Route.Settings::class, Route.Settings.serializer())
        subclass(Route.SettingsAppearance::class, Route.SettingsAppearance.serializer())
        subclass(Route.SettingsPreferences::class, Route.SettingsPreferences.serializer())
        subclass(Route.SettingsSync::class, Route.SettingsSync.serializer())
        subclass(Route.SettingsBackup::class, Route.SettingsBackup.serializer())
        subclass(Route.SettingsCsv::class, Route.SettingsCsv.serializer())
        subclass(Route.SettingsAbout::class, Route.SettingsAbout.serializer())
        subclass(Route.SettingsLicenses::class, Route.SettingsLicenses.serializer())
    }
}

private val savedStateConfig = SavedStateConfiguration {
    serializersModule = navKeySerializersModule
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavGraph(
    featureNavGraphs: List<FeatureNavGraph>,
) {
    val backStack = rememberNavBackStack(savedStateConfig, Route.SignIn)
    val bottomSheetStrategy = remember { BottomSheetSceneStrategy<NavKey>() }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val navigator = remember(backStack) { AppNavigator(backStack) }
    val appLaunchViewModel: AppLaunchViewModel = koinViewModel()
    val targetRoute by appLaunchViewModel.targetRoute.collectAsStateWithLifecycle()
    LaunchedEffect(targetRoute) {
        val route = targetRoute ?: return@LaunchedEffect
        val current = backStack.lastOrNull()
        if (current == route) return@LaunchedEffect
        navigator.resetTo(route)
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
                listDetailStrategy,
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
            if (topLevel == null) {
                navDisplay()
            } else {
                AppNavigationSuite(
                    currentTopLevel = topLevel,
                    onSelect = navigator::resetTo,
                    content = navDisplay,
                )
            }
        }
    }
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppNavigationSuite(
    currentTopLevel: Route.TopLevel,
    onSelect: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    val labels = TopLevelDestination.entries.associateWith { stringResource(it.label) }
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = if (
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    ) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    } else {
        NavigationSuiteType.None
    }
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val label = labels.getValue(destination)
                item(
                    selected = destination.matches(currentTopLevel),
                    onClick = { onSelect(destination.route) },
                    icon = { Icon(imageVector = destination.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
        containerColor = AppTheme.materialColors.background,
        content = content,
    )
}

@Suppress("SpreadOperator") // messageArgs holds 0-1 items; the array copy cost is negligible.
private suspend fun SnackbarRequest.resolveMessage(): String =
    getString(message, *messageArgs.toTypedArray())

private const val ANIMATION_DURATION = 300
