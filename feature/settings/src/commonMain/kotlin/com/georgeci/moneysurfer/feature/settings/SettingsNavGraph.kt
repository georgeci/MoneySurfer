package com.georgeci.moneysurfer.feature.settings

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.georgeci.moneysurfer.feature.settings.about.AboutScreen
import com.georgeci.moneysurfer.feature.settings.about.licenses.LicensesScreen
import com.georgeci.moneysurfer.feature.settings.account.DeleteUserAccountScreen
import com.georgeci.moneysurfer.feature.settings.appearance.AppearanceScreen
import com.georgeci.moneysurfer.feature.settings.backup.BackupScreen
import com.georgeci.moneysurfer.feature.settings.csv.CsvBackupScreen
import com.georgeci.moneysurfer.feature.settings.debug.DebugConfigScreen
import com.georgeci.moneysurfer.feature.settings.debug.DebugLogScreen
import com.georgeci.moneysurfer.feature.settings.preferences.PreferencesScreen
import com.georgeci.moneysurfer.feature.settings.sync.SyncScreen
import com.georgeci.moneysurfer.navigation.FeatureNavGraph
import com.georgeci.moneysurfer.navigation.NavDetailPlaceholder
import com.georgeci.moneysurfer.navigation.Route
import com.georgeci.moneysurfer.navigation.SurferPaneSceneStrategy

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
val settingsNavGraph: FeatureNavGraph = { navigator ->
    entry<Route.Settings>(
        metadata = SurferPaneSceneStrategy.listPane(detailPlaceholder = { NavDetailPlaceholder() }),
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
            onNavigateToBudgets = { navigator.push(Route.Budgets) },
            onNavigateToGoals = { navigator.push(Route.Goals) },
            onNavigateToAppearance = { navigator.push(Route.SettingsAppearance) },
            onNavigateToPreferences = { navigator.push(Route.SettingsPreferences) },
            onNavigateToSync = { navigator.push(Route.SettingsSync) },
            onNavigateToBackup = { navigator.push(Route.SettingsBackup) },
            onNavigateToCsvBackup = { navigator.push(Route.SettingsCsv) },
            onNavigateToAbout = { navigator.push(Route.SettingsAbout) },
            onNavigateToDeleteAccount = { navigator.push(Route.SettingsDeleteAccount) },
            onNavigateToDebugConfig = { navigator.push(Route.SettingsDebugConfig) },
        )
    }

    entry<Route.SettingsAppearance>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        AppearanceScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsPreferences>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        PreferencesScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsSync>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        SyncScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsBackup>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        BackupScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsCsv>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        CsvBackupScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsAbout>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        AboutScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToLicenses = { navigator.push(Route.SettingsLicenses) },
        )
    }

    entry<Route.SettingsLicenses>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        LicensesScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsDeleteAccount>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        DeleteUserAccountScreen(onNavigateBack = { navigator.pop() })
    }

    entry<Route.SettingsDebugConfig>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        DebugConfigScreen(
            onNavigateBack = { navigator.pop() },
            onNavigateToLogs = { navigator.push(Route.SettingsDebugLog) },
        )
    }

    entry<Route.SettingsDebugLog>(
        metadata = SurferPaneSceneStrategy.detailPane(),
    ) {
        DebugLogScreen(onNavigateBack = { navigator.pop() })
    }
}
