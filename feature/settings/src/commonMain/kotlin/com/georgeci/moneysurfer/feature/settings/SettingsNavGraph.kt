package com.georgeci.moneysurfer.feature.settings

import com.georgeci.moneysurfer.feature.settings.about.AboutScreen
import com.georgeci.moneysurfer.feature.settings.appearance.AppearanceScreen
import com.georgeci.moneysurfer.feature.settings.backup.BackupScreen
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesScreen
import com.georgeci.moneysurfer.feature.settings.sync.SyncScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.Route

val settingsNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Settings> {
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

    entry<Route.SettingsAppearance> {
        AppearanceScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsPreferences> {
        PreferencesScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsSync> {
        SyncScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsBackup> {
        BackupScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsAbout> {
        AboutScreen(onNavigateBack = { navigator.pop() })
    }
}
