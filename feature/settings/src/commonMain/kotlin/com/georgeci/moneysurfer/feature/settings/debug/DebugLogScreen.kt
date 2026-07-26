package com.georgeci.moneysurfer.feature.settings.debug

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.logging.DebugLogBuffer
import com.georgeci.moneysurfer.domain.logging.DebugLogEntry
import com.georgeci.moneysurfer.feature.settings.components.SettingsSubScreenScaffold
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_debug_log_clear
import moneysurfer.feature.settings.generated.resources.settings_debug_log_empty
import moneysurfer.feature.settings.generated.resources.settings_debug_log_footnote
import moneysurfer.feature.settings.generated.resources.settings_debug_log_title
import org.jetbrains.compose.resources.stringResource

/** Stable selectors for the QA log panel — see docs/testing/testing-strategy.md. */
object DebugLogTestTags {
    const val Root = "debugLog:root"
    const val ClearRow = "debugLog:clear"
    const val Empty = "debugLog:empty"
}

/**
 * The last Warn/Error lines Kermit produced in this process, straight off [DebugLogBuffer].
 *
 * No ViewModel: the buffer is already a process-wide `StateFlow` with nothing to fetch, map or
 * persist, and clearing it is a direct call. Threading it through Koin would buy a constructor
 * parameter and nothing else.
 */
@Composable
fun DebugLogScreen(onNavigateBack: () -> Unit) {
    val entries by DebugLogBuffer.entries.collectAsState()

    DebugLogContent(
        entries = entries,
        onBack = onNavigateBack,
        onClear = DebugLogBuffer::clear,
    )
}

/** Stateless content, public so `:composeApp`'s desktop UI tests can mount it — as with the other screens. */
@Composable
fun DebugLogContent(
    entries: List<DebugLogEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsSubScreenScaffold(
        title = stringResource(Res.string.settings_debug_log_title),
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        showProgressOverlay = false,
    ) { padding ->
        if (entries.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_debug_log_empty),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = AppTheme.spacing.default)
                    .testTag(DebugLogTestTags.Empty),
            )
            return@SettingsSubScreenScaffold
        }

        // Above the list, not under it: a full buffer is 100 entries deep with stack traces, and a
        // Clear button at the bottom of that is a button nobody reaches.
        SurferSettingsGroup {
            SurferSettingsRow(
                icon = SurferIcons.Delete,
                title = stringResource(Res.string.settings_debug_log_clear),
                danger = true,
                onClick = onClear,
                modifier = Modifier.testTag(DebugLogTestTags.ClearRow),
            )
        }

        SurferSettingsGroup(
            modifier = Modifier.testTag(DebugLogTestTags.Root),
            footnote = stringResource(Res.string.settings_debug_log_footnote),
        ) {
            // A plain column rather than a LazyColumn: the scaffold already scrolls vertically, and
            // nesting a lazy list in it measures with infinite height. The buffer is capped at 100
            // entries, so composing them all is cheap enough for a debug screen.
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default)) {
                entries.forEach { entry -> DebugLogEntryRow(entry) }
            }
        }

        Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
    }
}

/**
 * Severity and tag on one line, then the message, then the stack trace when the line carried a
 * throwable — which is the part worth reading on a device with no console attached, so it scrolls
 * sideways rather than wrapping into unreadable ribbons.
 */
@Composable
private fun DebugLogEntryRow(entry: DebugLogEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = entry.severity.name,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.error,
            )
            Text(
                text = entry.tag.ifBlank { "NoTag" },
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }

        Text(
            text = entry.message,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurface,
        )

        entry.throwable?.let { throwable ->
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = throwable.stackTraceToString(),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
