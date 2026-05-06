package com.georgeci.moneysurfer.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val savedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.SignIn::class, Route.SignIn.serializer())
            subclass(Route.WorkspaceSelector::class, Route.WorkspaceSelector.serializer())
            subclass(Route.WorkspaceCreation::class, Route.WorkspaceCreation.serializer())
            subclass(Route.WorkspaceMembers::class, Route.WorkspaceMembers.serializer())
            subclass(Route.WorkspaceManage::class, Route.WorkspaceManage.serializer())
            subclass(Route.WorkspaceInvite::class, Route.WorkspaceInvite.serializer())
            subclass(Route.WorkspaceMemberActions::class, Route.WorkspaceMemberActions.serializer())
            subclass(Route.IncomingInvites::class, Route.IncomingInvites.serializer())
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
            subclass(Route.SettingsAbout::class, Route.SettingsAbout.serializer())
        }
    }
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

    SyncStatusProvider {
        if (currentTopLevel == null) {
            navDisplay()
        } else {
            val destinationLabels = TopLevelDestination.entries.associateWith { destination ->
                stringResource(destination.label)
            }
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
                        val label = destinationLabels.getValue(destination)
                        item(
                            selected = destination.matches(currentTopLevel),
                            onClick = { navigator.resetTo(destination.route) },
                            icon = {
                                Icon(imageVector = destination.icon, contentDescription = label)
                            },
                            label = { Text(label) },
                        )
                    }
                },
                containerColor = AppTheme.materialColors.background,
                content = navDisplay,
            )
        }
    }
}

private const val ANIMATION_DURATION = 300
