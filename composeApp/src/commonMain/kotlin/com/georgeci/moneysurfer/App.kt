package com.georgeci.moneysurfer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.feature.account.accountNavGraph
import com.georgeci.moneysurfer.feature.category.categoryNavGraph
import com.georgeci.moneysurfer.feature.dashboard.dashboardNavGraph
import com.georgeci.moneysurfer.feature.login.loginNavGraph
import com.georgeci.moneysurfer.feature.settings.settingsNavGraph
import com.georgeci.moneysurfer.feature.transaction.transactionNavGraph
import com.georgeci.moneysurfer.feature.workspace.workspaceNavGraph
import com.georgeci.moneysurfer.navigation.AppNavGraph
import com.georgeci.moneysurfer.shared.app.AppViewModel
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle
import com.georgeci.moneysurfer.uikit.tokens.AccentSeeds
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
) {
    val appState by appViewModel.state.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appState.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val (useDynamic, seedColor) = resolvePalette(appState.paletteSource)

    StatusBarAppearance(isDark = darkTheme)

    AppTheme(
        darkTheme = darkTheme,
        seedColor = seedColor,
        dynamicColor = useDynamic,
        containerStyle = appState.containerStyle.toUiStyle(),
    ) {
        AppNavGraph(
            featureNavGraphs = listOf(
                loginNavGraph,
                workspaceNavGraph,
                dashboardNavGraph,
                accountNavGraph,
                categoryNavGraph,
                transactionNavGraph,
                settingsNavGraph,
            ),
        )
    }
}

private fun resolvePalette(source: PaletteSource): Pair<Boolean, Color?> = when (source) {
    PaletteSource.Brand -> false to null
    PaletteSource.Dynamic -> true to null
    is PaletteSource.Preset -> false to source.seed.toAccentColor()
}

private fun AccentSeed.toAccentColor(): Color = when (this) {
    AccentSeed.Plum -> AccentSeeds.Plum
    AccentSeed.Sky -> AccentSeeds.Sky
    AccentSeed.Sand -> AccentSeeds.Sand
    AccentSeed.Mint -> AccentSeeds.Mint
    AccentSeed.Rose -> AccentSeeds.Rose
}

private fun ContainerStyle.toUiStyle(): SurferContainerStyle = when (this) {
    ContainerStyle.Filled -> SurferContainerStyle.Filled
    ContainerStyle.Outlined -> SurferContainerStyle.Outlined
    ContainerStyle.Card -> SurferContainerStyle.Card
}
