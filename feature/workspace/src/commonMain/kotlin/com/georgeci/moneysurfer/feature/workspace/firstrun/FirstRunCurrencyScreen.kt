package com.georgeci.moneysurfer.feature.workspace.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferFullScreenLoader
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.first_run_currency_confirm
import moneysurfer.feature.workspace.generated.resources.first_run_currency_error
import moneysurfer.feature.workspace.generated.resources.first_run_currency_search_placeholder
import moneysurfer.feature.workspace.generated.resources.first_run_currency_skip
import moneysurfer.feature.workspace.generated.resources.first_run_currency_subtitle
import moneysurfer.feature.workspace.generated.resources.first_run_currency_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FirstRunCurrencyScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: FirstRunCurrencyViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            FirstRunCurrencyEffect.NavigateToDashboard -> onNavigateToDashboard()
        }
    }

    when (val current = state) {
        FirstRunCurrencyState.Loading -> FirstRunCurrencyLoading()
        is FirstRunCurrencyState.Content -> FirstRunCurrencyContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstRunCurrencyLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.surferSafeInsets(),
            topBar = { TopAppBar(title = { Text(stringResource(Res.string.first_run_currency_title)) }) },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        }
        SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstRunCurrencyContent(
    state: FirstRunCurrencyState.Content,
    onEvent: (FirstRunCurrencyEvent) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.surferSafeInsets(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.first_run_currency_title),
                            style = AppTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        TextButton(
                            onClick = { onEvent(FirstRunCurrencyEvent.OnSkipClick) },
                            enabled = !state.inFlight,
                        ) {
                            Text(
                                text = stringResource(Res.string.first_run_currency_skip),
                                color = AppTheme.materialColors.primary,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                Text(
                    text = stringResource(Res.string.first_run_currency_subtitle),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onEvent(FirstRunCurrencyEvent.OnQueryChanged(it)) },
                    placeholder = { Text(stringResource(Res.string.first_run_currency_search_placeholder)) },
                    leadingIcon = { Icon(imageVector = SurferIcons.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = AppTheme.materialColors.surfaceContainerHigh,
                        unfocusedContainerColor = AppTheme.materialColors.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .heightIn(min = 44.dp),
                )
                if (state.error) {
                    ErrorBanner(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.visibleCurrencies, key = { it.code.value }) { currency ->
                        CurrencyRow(
                            currency = currency,
                            selected = currency.code.value == state.selectedCode,
                            onClick = { onEvent(FirstRunCurrencyEvent.OnCurrencySelected(currency.code.value)) },
                        )
                    }
                }
                SurferButton(
                    text = stringResource(Res.string.first_run_currency_confirm),
                    onClick = { onEvent(FirstRunCurrencyEvent.OnConfirmClick) },
                    enabled = state.selectedCode != null && !state.inFlight,
                    size = SurferButtonSize.Biggest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = padding.calculateBottomPadding() + 16.dp),
                )
            }
        }

        if (state.inFlight) {
            SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CurrencyRow(
    currency: Currency,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val highlight = AppTheme.materialColors.primary.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) highlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = currency.symbol,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.code.value,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = currency.displayName,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = null,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}

@Composable
private fun ErrorBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.materialColors.errorContainer),
    ) {
        Text(
            text = stringResource(Res.string.first_run_currency_error),
            color = AppTheme.materialColors.onErrorContainer,
            style = AppTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview
@Composable
private fun FirstRunCurrencyScreenPreview() {
    AppTheme {
        FirstRunCurrencyContent(
            state = FirstRunCurrencyState.Content(
                currencies = listOf(
                    Currency(CurrencyCode("USD"), "$", "US Dollar"),
                    Currency(CurrencyCode("EUR"), "€", "Euro"),
                    Currency(CurrencyCode("GEL"), "₾", "Georgian Lari"),
                ),
                selectedCode = "GEL",
            ),
            onEvent = {},
        )
    }
}
