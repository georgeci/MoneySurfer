package com.georgeci.moneysurfer.feature.settings.about.licenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_licenses_error
import moneysurfer.feature.settings.generated.resources.settings_licenses_summary_format
import moneysurfer.feature.settings.generated.resources.settings_licenses_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            LicensesEffect.NavigateBack -> onNavigateBack()
        }
    }

    LicensesContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun LicensesContent(
    state: LicensesState,
    onEvent: (LicensesEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_licenses_title),
                onBack = { onEvent(LicensesEvent.OnBackClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> LicensesLoading(padding)
            state.isError -> LicensesError(padding)
            else -> LicensesList(state = state, padding = padding, onEvent = onEvent)
        }
    }
}

@Composable
private fun LicensesLoading(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppTheme.materialColors.primary)
    }
}

@Composable
private fun LicensesError(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().surferContentContainer().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.settings_licenses_error),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun LicensesList(
    state: LicensesState,
    padding: PaddingValues,
    onEvent: (LicensesEvent) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().surferContentContainer().padding(top = padding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "summary") {
            Text(
                text = stringResource(
                    Res.string.settings_licenses_summary_format,
                    state.libraries.sumOf { it.artifacts.size },
                ),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        items(items = state.libraries, key = { it.id }) { library ->
            LibraryRow(
                library = library,
                expanded = state.expandedLibraryId == library.id,
                onClick = { onEvent(LicensesEvent.OnLibraryClick(library.id)) },
            )
        }
    }
}

@Composable
private fun LibraryRow(
    library: OssLibrary,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.materialColors
    SurferCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = library.name,
                    style = AppTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                library.version?.let { version ->
                    Text(
                        text = version,
                        style = AppTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            val subtitle = listOfNotNull(
                library.author,
                library.licenses.joinToString { it.name }.ifEmpty { null },
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = library.artifacts.joinToString(separator = "\n"),
                        style = AppTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    library.licenses.forEach { license ->
                        license.content?.let { content ->
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = content,
                                style = AppTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
