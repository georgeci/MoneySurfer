package com.georgeci.moneysurfer.feature.workspace.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyBottomSheet
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyOption
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyPickerField
import com.georgeci.moneysurfer.uikit.components.SurferFullScreenLoader
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.components.workspace.SurferInviteRow
import com.georgeci.moneysurfer.uikit.components.workspace.SurferMemberRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.first_run_currency_search_placeholder
import moneysurfer.feature.workspace.generated.resources.workspace_creation_action_create
import moneysurfer.feature.workspace.generated.resources.workspace_creation_action_update
import moneysurfer.feature.workspace.generated.resources.workspace_creation_close_content_description
import moneysurfer.feature.workspace.generated.resources.workspace_creation_error_remote_sync
import moneysurfer.feature.workspace.generated.resources.workspace_creation_error_unknown
import moneysurfer.feature.workspace.generated.resources.workspace_creation_invite_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_creation_invite_title
import moneysurfer.feature.workspace.generated.resources.workspace_creation_name_label
import moneysurfer.feature.workspace.generated.resources.workspace_creation_preview_fallback_title
import moneysurfer.feature.workspace.generated.resources.workspace_creation_preview_subtitle_format
import moneysurfer.feature.workspace.generated.resources.workspace_creation_preview_subtitle_offline_format
import moneysurfer.feature.workspace.generated.resources.workspace_creation_section_currency
import moneysurfer.feature.workspace.generated.resources.workspace_creation_section_members
import moneysurfer.feature.workspace.generated.resources.workspace_creation_title_create
import moneysurfer.feature.workspace.generated.resources.workspace_creation_title_edit
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_editor_label
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_owner_label
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_viewer_label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun WorkspaceCreationScreen(
    workspaceId: WorkspaceId?,
    onNavigateBack: () -> Unit,
    viewModel: WorkspaceCreationViewModel = koinViewModel(),
) {
    LaunchedEffect(workspaceId) { viewModel.init(workspaceId) }
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceCreationEffect.NavigateBack -> onNavigateBack()
        }
    }

    when (val current = state) {
        WorkspaceCreationState.Loading -> WorkspaceCreationLoading(onEvent = viewModel::onEvent)
        is WorkspaceCreationState.Content -> WorkspaceCreationContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceCreationLoading(onEvent: (WorkspaceCreationEvent) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.surferSafeInsets(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.workspace_creation_title_create)) },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(WorkspaceCreationEvent.OnBackClick) }) {
                            Icon(
                                imageVector = SurferIcons.Close,
                                contentDescription = stringResource(
                                    Res.string.workspace_creation_close_content_description,
                                ),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        }

        SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceCreationContent(
    state: WorkspaceCreationState.Content,
    onEvent: (WorkspaceCreationEvent) -> Unit,
) {
    val titleRes = if (state.isEditing) {
        Res.string.workspace_creation_title_edit
    } else {
        Res.string.workspace_creation_title_create
    }
    val actionRes = if (state.isEditing) {
        Res.string.workspace_creation_action_update
    } else {
        Res.string.workspace_creation_action_create
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.surferSafeInsets(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(titleRes),
                            style = AppTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onEvent(WorkspaceCreationEvent.OnBackClick) }) {
                            Icon(
                                imageVector = SurferIcons.Close,
                                contentDescription = stringResource(
                                    Res.string.workspace_creation_close_content_description,
                                ),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { onEvent(WorkspaceCreationEvent.OnSaveClick) },
                            enabled = state.name.isNotBlank() && !state.isSaving,
                        ) {
                            Text(
                                text = stringResource(actionRes),
                                style = AppTheme.typography.labelLarge,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                state.error?.let { ErrorBanner(it) }

                PreviewCard(
                    name = state.name,
                    memberCount = state.members.size,
                    currencyCode = state.currency,
                    isOffline = state.isOffline,
                )

                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onEvent(WorkspaceCreationEvent.OnNameChanged(it)) },
                    label = { Text(stringResource(Res.string.workspace_creation_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.currencies.isNotEmpty() && !(state.isEditing && state.isOffline)) {
                    CurrencyPicker(
                        currencies = state.currencies,
                        selected = state.currency,
                        onSelect = { onEvent(WorkspaceCreationEvent.OnCurrencyChanged(it)) },
                    )
                }

                if (state.showMembersSection) {
                    MembersSection(
                        members = state.members,
                        showInviteRow = true,
                        onInviteClick = { onEvent(WorkspaceCreationEvent.OnInviteMemberClick) },
                    )
                }

                Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
            }
        }

        if (state.isSaving) {
            SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A field that opens the currency bottom sheet, mirroring account creation. The supported list
 * is already eight entries and still growing, which a segmented control cannot hold on a phone
 * (see issue #277).
 */
@Composable
private fun CurrencyPicker(
    currencies: List<Currency>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val resolved = currencies.firstOrNull { it.code.value == selected } ?: currencies.first()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    SurferSectionLabel(
        text = stringResource(Res.string.workspace_creation_section_currency),
        modifier = Modifier.padding(bottom = 10.dp),
    )
    SurferCurrencyPickerField(
        symbol = resolved.symbol,
        code = resolved.code.value,
        name = resolved.displayName,
        onClick = { sheetOpen = true },
        expanded = sheetOpen,
    )

    if (sheetOpen) {
        SurferCurrencyBottomSheet(
            title = stringResource(Res.string.workspace_creation_section_currency),
            searchPlaceholder = stringResource(Res.string.first_run_currency_search_placeholder),
            currencies = currencies.map {
                SurferCurrencyOption(code = it.code.value, symbol = it.symbol, name = it.displayName)
            },
            selectedCode = resolved.code.value,
            onSelect = {
                onSelect(it.code)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun PreviewCard(
    name: String,
    memberCount: Int,
    currencyCode: String,
    isOffline: Boolean,
) {
    val initials = name.toInitials().ifBlank { "W" }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.primaryContainer,
            contentColor = AppTheme.materialColors.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.materialColors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = AppTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = AppTheme.materialColors.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { stringResource(Res.string.workspace_creation_preview_fallback_title) },
                    style = AppTheme.typography.titleLarge,
                )
                Text(
                    text = if (isOffline) {
                        stringResource(
                            Res.string.workspace_creation_preview_subtitle_offline_format,
                            currencyCode.ifBlank { "—" },
                        )
                    } else {
                        pluralStringResource(
                            Res.plurals.workspace_creation_preview_subtitle_format,
                            memberCount,
                            memberCount,
                            currencyCode.ifBlank { "—" },
                        )
                    },
                    style = AppTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(error: WorkspaceCreationError) {
    val text = when (error) {
        WorkspaceCreationError.RemoteSyncFailed -> stringResource(Res.string.workspace_creation_error_remote_sync)
        WorkspaceCreationError.Unknown -> stringResource(Res.string.workspace_creation_error_unknown)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppTheme.materialColors.errorContainer),
    ) {
        Text(
            text = text,
            color = AppTheme.materialColors.onErrorContainer,
            style = AppTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun MembersSection(
    members: List<WorkspaceMemberUi>,
    showInviteRow: Boolean,
    onInviteClick: () -> Unit,
) {
    SurferSectionLabel(
        text = stringResource(Res.string.workspace_creation_section_members),
        modifier = Modifier.padding(bottom = 10.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showInviteRow) {
            SurferInviteRow(
                title = stringResource(Res.string.workspace_creation_invite_title),
                subtitle = stringResource(Res.string.workspace_creation_invite_subtitle),
                onClick = onInviteClick,
            )
        }
        if (members.isNotEmpty()) {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                members.forEach { member ->
                    SurferMemberRow(
                        name = member.name,
                        initial = member.initial,
                        roleLabel = roleLabel(member.role),
                    )
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: WorkspaceRole): String = when (role) {
    WorkspaceRole.OWNER -> stringResource(Res.string.workspace_members_role_owner_label)
    WorkspaceRole.EDITOR -> stringResource(Res.string.workspace_members_role_editor_label)
    WorkspaceRole.VIEWER -> stringResource(Res.string.workspace_members_role_viewer_label)
}

private fun String.toInitials(): String =
    trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }

@Preview
@Composable
private fun WorkspaceCreationScreenPreview() {
    AppTheme {
        WorkspaceCreationContent(
            state = WorkspaceCreationState.Content(
                workspaceId = null,
                name = "Lisbon trip",
                description = "",
                currency = "EUR",
                currencies = previewCurrencies,
                members = emptyList(),
                isEditing = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceEditScreenPreview() {
    AppTheme {
        WorkspaceCreationContent(
            state = WorkspaceCreationState.Content(
                workspaceId = WorkspaceId("preview-ws-1"),
                name = "Personal",
                description = "Everyday spending and savings",
                currency = "USD",
                currencies = previewCurrencies,
                members = emptyList(),
                isEditing = true,
            ),
            onEvent = {},
        )
    }
}

private val previewCurrencies = listOf(
    Currency(CurrencyCode("EUR"), "€", "Euro"),
    Currency(CurrencyCode("USD"), "$", "US Dollar"),
    Currency(CurrencyCode("GBP"), "£", "British Pound"),
    Currency(CurrencyCode("PLN"), "zł", "Polish Zloty"),
)
