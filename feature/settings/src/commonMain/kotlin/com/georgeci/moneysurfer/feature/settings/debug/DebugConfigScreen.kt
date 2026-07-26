package com.georgeci.moneysurfer.feature.settings.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.ConfigDebugRowKind
import com.georgeci.moneysurfer.feature.settings.components.SettingsSubScreenScaffold
import com.georgeci.moneysurfer.uikit.components.base.SurferFilterChipRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_debug_config_degraded_format
import moneysurfer.feature.settings.generated.resources.settings_debug_config_footnote
import moneysurfer.feature.settings.generated.resources.settings_debug_config_host_owned
import moneysurfer.feature.settings.generated.resources.settings_debug_config_invalid_format
import moneysurfer.feature.settings.generated.resources.settings_debug_config_layer_absent
import moneysurfer.feature.settings.generated.resources.settings_debug_config_layer_undecodable_format
import moneysurfer.feature.settings.generated.resources.settings_debug_config_reset_all
import moneysurfer.feature.settings.generated.resources.settings_debug_config_section_keys
import moneysurfer.feature.settings.generated.resources.settings_debug_config_title
import moneysurfer.feature.settings.generated.resources.settings_debug_config_unavailable
import moneysurfer.feature.settings.generated.resources.settings_debug_config_winner_format
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the QA configuration panel — see docs/testing/testing-strategy.md. */
object DebugConfigTestTags {
    const val Root = "debugConfig:root"
    const val ResetAllRow = "debugConfig:resetAll"
    const val DegradedBanner = "debugConfig:degradedBanner"
    const val Unavailable = "debugConfig:unavailable"

    fun row(name: String): String = "debugConfig:row:$name"
}

@Composable
fun DebugConfigScreen(
    onNavigateBack: () -> Unit,
    viewModel: DebugConfigViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingInvalid by remember { mutableStateOf<DebugConfigEffect.NotifyInvalidValue?>(null) }

    // Resolved during composition so the positional placeholders go through Compose's own format
    // path and survive translator reordering; the effect below only feeds the snackbar.
    val invalidText: String? = pendingInvalid?.let {
        stringResource(Res.string.settings_debug_config_invalid_format, it.raw, it.name)
    }
    LaunchedEffect(invalidText) {
        val text = invalidText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        pendingInvalid = null
    }

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            DebugConfigEffect.NavigateBack -> onNavigateBack()
            is DebugConfigEffect.NotifyInvalidValue -> pendingInvalid = effect
        }
    }

    DebugConfigContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

/** Stateless content, public so `:composeApp`'s desktop UI tests can mount it — as with the other screens. */
@Composable
fun DebugConfigContent(
    state: DebugConfigState,
    snackbarHostState: SnackbarHostState,
    onEvent: (DebugConfigEvent) -> Unit,
) {
    SettingsSubScreenScaffold(
        title = stringResource(Res.string.settings_debug_config_title),
        snackbarHostState = snackbarHostState,
        onBack = { onEvent(DebugConfigEvent.OnBackClick) },
        showProgressOverlay = false,
    ) { padding ->
        if (!state.isAvailable) {
            // Release builds bind `DebugConfigSource.Empty`, which accepts writes and drops them.
            // A restored back stack can still land here, and editable rows that silently no-op are
            // worse than none.
            Text(
                text = stringResource(Res.string.settings_debug_config_unavailable),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = AppTheme.spacing.default)
                    .testTag(DebugConfigTestTags.Unavailable),
            )
            return@SettingsSubScreenScaffold
        }

        if (state.degradedLayers.isNotEmpty()) {
            // A layer whose store failed to load resolves as absent, so every row below it shows a
            // fallback. Saying so beats letting the panel present that fallback as the truth.
            Text(
                text = stringResource(
                    Res.string.settings_debug_config_degraded_format,
                    state.degradedLayers.joinToString(),
                ),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.error,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = AppTheme.spacing.small)
                    .testTag(DebugConfigTestTags.DegradedBanner),
            )
        }

        SurferSettingsGroup(
            modifier = Modifier.testTag(DebugConfigTestTags.Root),
            title = stringResource(Res.string.settings_debug_config_section_keys),
            footnote = stringResource(Res.string.settings_debug_config_footnote),
        ) {
            state.rows.forEach { row -> ConfigKeyRow(row = row, onEvent = onEvent) }
        }

        SurferSettingsGroup {
            SurferSettingsRow(
                icon = SurferIcons.Delete,
                title = stringResource(Res.string.settings_debug_config_reset_all),
                danger = true,
                onClick = { onEvent(DebugConfigEvent.OnResetAllClick) },
                modifier = Modifier.testTag(DebugConfigTestTags.ResetAllRow),
            )
        }

        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }
}

/**
 * One key. The control comes from the row's value kind — a boolean gets a switch, a closed set gets
 * chips — so nobody has to type `PRESET:Plum` by hand. Tapping an overridden row clears its
 * override.
 */
@Composable
private fun ConfigKeyRow(
    row: ConfigDebugRow,
    onEvent: (DebugConfigEvent) -> Unit,
) {
    val clearOverride: (() -> Unit)? =
        if (row.overridden) ({ onEvent(DebugConfigEvent.OnClearOverride(row.name)) }) else null

    when (val kind = row.kind) {
        ConfigDebugRowKind.Bool -> SurferSettingsRow(
            icon = SurferIcons.Code,
            title = row.name,
            supporting = { ResolutionSummary(row) },
            trailing = {
                SurferSettingsSwitch(
                    checked = row.effectiveValue.toBoolean(),
                    onCheckedChange = { checked ->
                        onEvent(DebugConfigEvent.OnOverride(row.name, checked.toString()))
                    },
                )
            },
            onClick = clearOverride,
            multiline = true,
            modifier = Modifier.testTag(DebugConfigTestTags.row(row.name)),
        )

        is ConfigDebugRowKind.Choice -> SurferSettingsRow(
            icon = SurferIcons.Code,
            title = row.name,
            supporting = {
                Column {
                    ResolutionSummary(row)
                    Spacer(Modifier.height(AppTheme.spacing.small))
                    // A closed set can be longer than the screen (the palette's presets), so the
                    // chip row scrolls rather than clipping the tail options out of reach.
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SurferFilterChipRow(
                            options = kind.choices,
                            selected = row.effectiveValue,
                            label = { it },
                            onSelect = { choice -> onEvent(DebugConfigEvent.OnOverride(row.name, choice)) },
                        )
                    }
                }
            },
            onClick = clearOverride,
            multiline = true,
            modifier = Modifier.testTag(DebugConfigTestTags.row(row.name)),
        )

        // Free-form values (the encoded dashboard layout) are inspect-only: a text field here is a
        // routine way to store something no codec can read back.
        ConfigDebugRowKind.FreeText -> SurferSettingsRow(
            icon = SurferIcons.Code,
            title = row.name,
            supporting = { ResolutionSummary(row) },
            onClick = clearOverride,
            multiline = true,
            modifier = Modifier.testTag(DebugConfigTestTags.row(row.name)),
        )
    }
}

/**
 * Effective value, the layer that won, and every layer's cell — absent and undecodable read
 * differently on purpose, because "stored but rejected by the codec" is the failure the per-layer
 * view exists to expose.
 */
@Composable
private fun ResolutionSummary(row: ConfigDebugRow) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = row.effectiveValue + " · " +
                stringResource(Res.string.settings_debug_config_winner_format, row.winner),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        if (row.hostOwned) {
            // Overriding one of these makes the app believe it is a different build while its DI
            // graph stays as compiled — worth saying out loud next to an ordinary feature flag.
            Text(
                text = stringResource(Res.string.settings_debug_config_host_owned),
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.error,
            )
        }
        row.layers.forEach { cell ->
            val rendered = when {
                cell.undecodable ->
                    stringResource(Res.string.settings_debug_config_layer_undecodable_format, cell.value.orEmpty())
                cell.value == null -> stringResource(Res.string.settings_debug_config_layer_absent)
                else -> cell.value
            }
            Text(
                text = "${cell.layer}: $rendered",
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
