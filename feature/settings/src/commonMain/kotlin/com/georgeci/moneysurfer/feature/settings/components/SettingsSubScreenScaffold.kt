package com.georgeci.moneysurfer.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneTopBar
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Shared chrome for settings sub-screens (Backup, CSV export/import): themed
 * toolbar, snackbar host, scrollable column, and an optional modal progress
 * scrim while a long-running operation is in flight. [content] receives the
 * scaffold padding so screens can size their bottom spacer.
 */
@Composable
internal fun SettingsSubScreenScaffold(
    title: String,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    showProgressOverlay: Boolean,
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferPaneTopBar(
                title = title,
                onBack = onBack,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .surferContentContainer()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState()),
            ) {
                content(padding)
            }

            if (showProgressOverlay) {
                SettingsProgressOverlay()
            }
        }
    }
}

/**
 * Modal scrim while export/import is in flight. The `clickable` with no-op
 * onClick + null indication consumes pointer events so taps don't reach the
 * settings rows underneath.
 */
@Composable
private fun SettingsProgressOverlay() {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.materialColors.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppTheme.materialColors.primary)
    }
}

private const val SCRIM_ALPHA = 0.4f
