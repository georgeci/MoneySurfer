package com.georgeci.moneysurfer.feature.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import com.georgeci.moneysurfer.feature.settings.about.AboutScreen
import com.georgeci.moneysurfer.feature.settings.appearance.AppearanceScreen
import com.georgeci.moneysurfer.feature.settings.backup.BackupScreen
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesScreen
import com.georgeci.moneysurfer.feature.settings.sync.SyncScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val settingsNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Settings>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
    ) {
        SettingsScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToWorkspaceSelector = {
                navigator.push(Route.WorkspaceSelector(showActions = true))
            },
            onNavigateToIncomingInvites = { navigator.push(Route.IncomingInvites) },
            onNavigateToMembers = { workspaceId ->
                navigator.push(Route.WorkspaceManage(workspaceId = workspaceId.value))
            },
            onNavigateToCategories = { navigator.push(Route.CategoriesManage) },
            onNavigateToAppearance = { navigator.push(Route.SettingsAppearance) },
            onNavigateToPreferences = { navigator.push(Route.SettingsPreferences) },
            onNavigateToSync = { navigator.push(Route.SettingsSync) },
            onNavigateToBackup = { navigator.push(Route.SettingsBackup) },
            onNavigateToAbout = { navigator.push(Route.SettingsAbout) },
        )
    }

    entry<Route.SettingsAppearance>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        AppearanceScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsPreferences>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        PreferencesScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsSync>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        SyncScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsBackup>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        BackupScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsAbout>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) {
        AboutScreen(onNavigateBack = { navigator.pop() })
    }
}
