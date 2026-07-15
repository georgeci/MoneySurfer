package com.georgeci.moneysurfer.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferNameBlock
import com.georgeci.moneysurfer.uikit.components.settings.SurferPendingBadge
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_about
import moneysurfer.feature.settings.generated.resources.settings_about_supporting_format
import moneysurfer.feature.settings.generated.resources.settings_appearance_hub_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_hub_supporting_dynamic
import moneysurfer.feature.settings.generated.resources.settings_appearance_hub_title
import moneysurfer.feature.settings.generated.resources.settings_categories_supporting
import moneysurfer.feature.settings.generated.resources.settings_categories_title
import moneysurfer.feature.settings.generated.resources.settings_change_workspace
import moneysurfer.feature.settings.generated.resources.settings_change_workspace_supporting
import moneysurfer.feature.settings.generated.resources.settings_csv_supporting
import moneysurfer.feature.settings.generated.resources.settings_csv_title
import moneysurfer.feature.settings.generated.resources.settings_finish_setup
import moneysurfer.feature.settings.generated.resources.settings_finish_setup_supporting
import moneysurfer.feature.settings.generated.resources.settings_logout
import moneysurfer.feature.settings.generated.resources.settings_members
import moneysurfer.feature.settings.generated.resources.settings_members_count_format
import moneysurfer.feature.settings.generated.resources.settings_pending_invites
import moneysurfer.feature.settings.generated.resources.settings_pending_invites_supporting_empty
import moneysurfer.feature.settings.generated.resources.settings_pending_invites_supporting_format
import moneysurfer.feature.settings.generated.resources.settings_section_data
import moneysurfer.feature.settings.generated.resources.settings_section_help
import moneysurfer.feature.settings.generated.resources.settings_section_personalization
import moneysurfer.feature.settings.generated.resources.settings_section_workspace
import moneysurfer.feature.settings.generated.resources.settings_sync_hub_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_hub_title
import moneysurfer.feature.settings.generated.resources.settings_title
import moneysurfer.feature.settings.generated.resources.settings_user_name
import moneysurfer.feature.settings.generated.resources.settings_version_format
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stable selectors for the Settings screen — see docs/testing/testing-strategy.md.
 *
 * [SyncRow] and [LogoutRow] are never composed in the offline build; their absence
 * is what the offline golden Maestro flow asserts via `notVisible`.
 */
object SettingsTestTags {
    const val Root = "settings:root"
    const val CategoriesRow = "settings:categoriesRow"
    const val AppearanceRow = "settings:appearanceRow"
    const val AboutRow = "settings:aboutRow"
    const val SyncRow = "settings:syncRow"
    const val CsvRow = "settings:csvRow"
    const val LogoutRow = "settings:logoutRow"
}

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToWorkspaceSelector: () -> Unit,
    onNavigateToFinishSetup: () -> Unit,
    onNavigateToIncomingInvites: () -> Unit,
    onNavigateToMembers: (com.georgeci.moneysurfer.domain.primitives.WorkspaceId) -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPreferences: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToCsvBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            SettingsEffect.NavigateBack -> onNavigateBack()
            SettingsEffect.NavigateToWorkspaceSelector -> onNavigateToWorkspaceSelector()
            SettingsEffect.NavigateToFinishSetup -> onNavigateToFinishSetup()
            SettingsEffect.NavigateToIncomingInvites -> onNavigateToIncomingInvites()
            is SettingsEffect.NavigateToMembers -> onNavigateToMembers(effect.workspaceId)
            SettingsEffect.NavigateToCategories -> onNavigateToCategories()
            SettingsEffect.NavigateToAppearance -> onNavigateToAppearance()
            SettingsEffect.NavigateToPreferences -> onNavigateToPreferences()
            SettingsEffect.NavigateToSync -> onNavigateToSync()
            SettingsEffect.NavigateToBackup -> onNavigateToBackup()
            SettingsEffect.NavigateToCsvBackup -> onNavigateToCsvBackup()
            SettingsEffect.NavigateToAbout -> onNavigateToAbout()
        }
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .surferTestTagAsId()
            .testTag(SettingsTestTags.Root),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_title),
                onBack = { onEvent(SettingsEvent.OnBackClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.showProfile) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
                    SurferNameBlock(
                        name = stringResource(Res.string.settings_user_name),
                        email = userEmailText(state),
                        trailing = null,
                    )
                }
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_section_workspace)) {
                if (state.showFinishSetup) {
                    SurferSettingsRow(
                        icon = SurferIcons.Wallet,
                        title = stringResource(Res.string.settings_finish_setup),
                        supportingText = stringResource(Res.string.settings_finish_setup_supporting),
                        onClick = { onEvent(SettingsEvent.OnFinishSetupClick) },
                        trailing = { SurferSettingsChevron() },
                    )
                }
                SurferSettingsRow(
                    icon = SurferIcons.People,
                    title = stringResource(Res.string.settings_change_workspace),
                    supportingText = stringResource(Res.string.settings_change_workspace_supporting),
                    onClick = { onEvent(SettingsEvent.OnChangeWorkspaceClick) },
                    trailing = { SurferSettingsChevron() },
                )
                if (state.showWorkspaceMembers && state.currentWorkspaceId != null) {
                    SurferSettingsRow(
                        icon = SurferIcons.Sparkle,
                        title = stringResource(Res.string.settings_members),
                        supportingText = pluralStringResource(
                            Res.plurals.settings_members_count_format,
                            state.activeMemberCount,
                            state.activeMemberCount,
                        ),
                        onClick = { onEvent(SettingsEvent.OnMembersClick) },
                        trailing = { SurferSettingsChevron() },
                    )
                }
                if (state.showPendingInvites) {
                    SurferSettingsRow(
                        icon = SurferIcons.Mail,
                        title = stringResource(Res.string.settings_pending_invites),
                        supporting = if (state.pendingInviteCount > 0) {
                            {
                                SurferPendingBadge(
                                    text = pluralStringResource(
                                        Res.plurals.settings_pending_invites_supporting_format,
                                        state.pendingInviteCount,
                                        state.pendingInviteCount,
                                    ),
                                )
                            }
                        } else {
                            null
                        },
                        supportingText = if (state.pendingInviteCount == 0) {
                            stringResource(Res.string.settings_pending_invites_supporting_empty)
                        } else {
                            null
                        },
                        onClick = { onEvent(SettingsEvent.OnIncomingInvitesClick) },
                        trailing = { SurferSettingsChevron() },
                    )
                }
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_section_personalization)) {
                SurferSettingsRow(
                    icon = SurferIcons.Category,
                    title = stringResource(Res.string.settings_categories_title),
                    supportingText = stringResource(Res.string.settings_categories_supporting),
                    onClick = { onEvent(SettingsEvent.OnCategoriesClick) },
                    trailing = { SurferSettingsChevron() },
                    modifier = Modifier.testTag(SettingsTestTags.CategoriesRow),
                )
                SurferSettingsRow(
                    icon = SurferIcons.Palette,
                    title = stringResource(Res.string.settings_appearance_hub_title),
                    supportingText = appearanceSupporting(state.isDynamicColorEnabled),
                    onClick = { onEvent(SettingsEvent.OnAppearanceClick) },
                    trailing = { SurferSettingsChevron() },
                    modifier = Modifier.testTag(SettingsTestTags.AppearanceRow),
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_section_data)) {
                if (state.showSyncSection) {
                    SurferSettingsRow(
                        icon = SurferIcons.Sync,
                        title = stringResource(Res.string.settings_sync_hub_title),
                        supportingText = stringResource(Res.string.settings_sync_hub_supporting),
                        onClick = { onEvent(SettingsEvent.OnSyncClick) },
                        trailing = { SurferSettingsChevron() },
                        modifier = Modifier.testTag(SettingsTestTags.SyncRow),
                    )
                }
                SurferSettingsRow(
                    icon = SurferIcons.Download,
                    title = stringResource(Res.string.settings_csv_title),
                    supportingText = stringResource(Res.string.settings_csv_supporting),
                    onClick = { onEvent(SettingsEvent.OnCsvBackupClick) },
                    trailing = { SurferSettingsChevron() },
                    modifier = Modifier.testTag(SettingsTestTags.CsvRow),
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_section_help)) {
                SurferSettingsRow(
                    icon = SurferIcons.Info,
                    title = stringResource(Res.string.settings_about),
                    supportingText = stringResource(
                        Res.string.settings_about_supporting_format,
                        state.appVersion,
                    ),
                    onClick = { onEvent(SettingsEvent.OnAboutClick) },
                    trailing = { SurferSettingsChevron() },
                    modifier = Modifier.testTag(SettingsTestTags.AboutRow),
                )
            }

            if (state.showLogout) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SurferSettingsRow(
                        icon = SurferIcons.Logout,
                        title = stringResource(Res.string.settings_logout),
                        danger = true,
                        onClick = { onEvent(SettingsEvent.OnLogoutClick) },
                        modifier = Modifier.testTag(SettingsTestTags.LogoutRow),
                    )
                }
            }

            Spacer(Modifier.height(AppTheme.spacing.large))
            Text(
                text = stringResource(Res.string.settings_version_format, state.appVersion),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = AppTheme.spacing.small),
            )
            Spacer(Modifier.height(padding.calculateBottomPadding() + AppTheme.spacing.large))
        }
    }
}

@Composable
private fun appearanceSupporting(isDynamicColorEnabled: Boolean): String =
    if (isDynamicColorEnabled) {
        stringResource(Res.string.settings_appearance_hub_supporting_dynamic)
    } else {
        stringResource(Res.string.settings_appearance_hub_supporting)
    }

private fun userEmailText(state: SettingsState): String =
    when {
        state.isAnonymousUser -> "anon"
        !state.userEmail.isNullOrBlank() -> state.userEmail
        else -> "anon"
    }

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsContent(
            state = SettingsState(),
            onEvent = {},
        )
    }
}
