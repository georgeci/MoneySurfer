package com.georgeci.moneysurfer.feature.account.manage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_type_bank
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_type_card
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_type_cash
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_type_savings
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_action_failed
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_active_header
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_add_account
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_undo
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_footnote
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_header
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_hint
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archived_snackbar
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_done
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_drag_to_reorder
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_edit
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_empty
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_remove
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_restore
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_stat_active_count
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_stat_archived_count
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_stat_total
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_title
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountManageCard
import com.georgeci.moneysurfer.uikit.components.account.SurferArchivedAccountCard
import com.georgeci.moneysurfer.uikit.components.account.SurferTotalBalanceCard
import com.georgeci.moneysurfer.uikit.components.base.SurferDragHandle
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeRevealRow
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountsManageScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAccountCreation: () -> Unit,
    onNavigateToAccountEdit: (AccountId) -> Unit,
    onNavigateToAccountDetails: (AccountId) -> Unit,
    viewModel: AccountsManageViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val archivedTemplate = stringResource(Res.string.accounts_manage_archived_snackbar)
    val undoLabel = stringResource(Res.string.accounts_manage_archive_undo)
    val errorMessage = stringResource(Res.string.accounts_manage_action_failed)

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            AccountsManageEffect.NavigateBack -> onNavigateBack()
            AccountsManageEffect.NavigateToAccountCreation -> onNavigateToAccountCreation()
            is AccountsManageEffect.NavigateToAccountDetails -> onNavigateToAccountDetails(effect.accountId)
            is AccountsManageEffect.NavigateToAccountEdit -> onNavigateToAccountEdit(effect.accountId)
            is AccountsManageEffect.ShowArchivedSnackbar -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = archivedTemplate.replace("%1\$s", effect.name),
                        actionLabel = undoLabel,
                        withDismissAction = false,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(AccountsManageEvent.OnUndoArchive)
                    }
                }
            }
            AccountsManageEffect.ShowError -> {
                scope.launch { snackbarHostState.showSnackbar(errorMessage) }
            }
        }
    }

    when (val current = state) {
        AccountsManageState.Loading -> AccountsManageLoading(onEvent = viewModel::onEvent)
        is AccountsManageState.Content -> AccountsManageContent(
            state = current,
            snackbarHostState = snackbarHostState,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun AccountsManageLoading(onEvent: (AccountsManageEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.accounts_manage_title),
                onBack = { onEvent(AccountsManageEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun AccountsManageContent(
    state: AccountsManageState.Content,
    snackbarHostState: SnackbarHostState,
    onEvent: (AccountsManageEvent) -> Unit,
) {
    val hasContent = state.activeAccounts.isNotEmpty() || state.archivedAccounts.isNotEmpty()
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.accounts_manage_title),
                onBack = { onEvent(AccountsManageEvent.OnBackClick) },
                actions = {
                    EditToggleChip(
                        editing = state.isEditing,
                        onClick = { onEvent(AccountsManageEvent.OnToggleEditMode) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(Res.string.accounts_manage_add_account)) },
                icon = { Icon(imageVector = SurferIcons.Add, contentDescription = null) },
                onClick = { onEvent(AccountsManageEvent.OnAddAccountClick) },
            )
        },
    ) { padding ->
        if (!hasContent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = AppTheme.spacing.large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.accounts_manage_empty),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
        ) {
            item(key = "aggregate") {
                SurferTotalBalanceCard(
                    label = stringResource(Res.string.accounts_manage_stat_total),
                    formattedTotal = state.formattedTotal,
                    activeChipText = stringResource(
                        Res.string.accounts_manage_stat_active_count,
                        state.activeAccounts.size,
                    ),
                    archivedChipText = state.archivedAccounts.size
                        .takeIf { it > 0 }
                        ?.let {
                            stringResource(Res.string.accounts_manage_stat_archived_count, it)
                        },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item(key = "active-header") {
                SectionHeader(
                    title = stringResource(Res.string.accounts_manage_active_header),
                    trailing = if (state.isEditing) {
                        stringResource(Res.string.accounts_manage_drag_to_reorder)
                    } else {
                        null
                    },
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 6.dp),
                )
            }

            items(state.activeAccounts, key = { "active-${it.id.value}" }) { account ->
                ActiveAccountRow(
                    account = account,
                    editing = state.isEditing,
                    archiveLabel = stringResource(Res.string.accounts_manage_archive),
                    deleteLabel = stringResource(Res.string.accounts_manage_remove),
                    onClick = { onEvent(AccountsManageEvent.OnAccountClick(account.id)) },
                    onArchiveClick = { onEvent(AccountsManageEvent.OnArchiveAccountClick(account.id)) },
                    onRemoveClick = { onEvent(AccountsManageEvent.OnRemoveAccountClick(account.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }

            if (state.archivedAccounts.isNotEmpty()) {
                item(key = "archived-header") {
                    SectionHeader(
                        title = stringResource(Res.string.accounts_manage_archived_header),
                        trailing = stringResource(Res.string.accounts_manage_archived_hint),
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 6.dp),
                    )
                }

                items(state.archivedAccounts, key = { "archived-${it.id.value}" }) { account ->
                    SurferArchivedAccountCard(
                        name = account.name,
                        subtitle = archivedSubtitle(account),
                        icon = account.type.icon(),
                        restoreLabel = stringResource(Res.string.accounts_manage_restore),
                        onRestoreClick = { onEvent(AccountsManageEvent.OnRestoreAccountClick(account.id)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            item(key = "footnote") {
                Text(
                    text = stringResource(Res.string.accounts_manage_archived_footnote),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 18.dp, bottom = 8.dp),
                )
            }
        }
    }

    state.pendingDelete?.let { pending ->
        DeleteAccountDialog(
            accountName = pending.name,
            onConfirm = { onEvent(AccountsManageEvent.OnDeleteConfirm) },
            onDismiss = { onEvent(AccountsManageEvent.OnDeleteCancel) },
        )
    }

    state.pendingArchive?.let { pending ->
        ArchiveAccountDialog(
            accountName = pending.name,
            onConfirm = { onEvent(AccountsManageEvent.OnArchiveConfirm) },
            onDismiss = { onEvent(AccountsManageEvent.OnArchiveCancel) },
        )
    }
}

@Composable
private fun ActiveAccountRow(
    account: AccountManageUi,
    editing: Boolean,
    archiveLabel: String,
    deleteLabel: String,
    onClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = stringResource(account.type.labelRes())
    SurferSwipeRevealRow(
        revealWidth = 156.dp,
        modifier = modifier.fillMaxWidth(),
        enabled = !editing,
        actions = {
            SurferSwipeAction(
                icon = SurferIcons.Archive,
                label = archiveLabel,
                onClick = onArchiveClick,
            )
            SurferSwipeAction(
                icon = SurferIcons.Delete,
                label = deleteLabel,
                onClick = onRemoveClick,
                destructive = true,
            )
        },
    ) {
        SurferAccountManageCard(
            name = account.name,
            subtitle = typeLabel,
            icon = account.type.icon(),
            formattedBalance = account.formattedBalance,
            onClick = if (editing) null else onClick,
            trailing = if (editing) {
                { SurferDragHandle() }
            } else {
                null
            },
        )
    }
}

@Composable
private fun EditToggleChip(
    editing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = if (editing) SurferIcons.Check else SurferIcons.Edit
    val label = stringResource(if (editing) Res.string.accounts_manage_done else Res.string.accounts_manage_edit)
    if (editing) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 32.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = AppTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 32.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = AppTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

private fun archivedSubtitle(account: AccountManageUi): String {
    val typeLabelKey = when (account.type) {
        AccountType.CASH -> "Cash"
        AccountType.BANK -> "Bank"
        AccountType.CARD -> "Card"
        AccountType.SAVINGS -> "Savings"
    }
    return account.archivedLabel?.let { "$typeLabelKey · $it" } ?: typeLabelKey
}

private fun AccountType.icon(): ImageVector = when (this) {
    AccountType.CASH -> SurferIcons.Cash
    AccountType.BANK -> SurferIcons.Bank
    AccountType.CARD -> SurferIcons.CreditCard
    AccountType.SAVINGS -> SurferIcons.Savings
}

private fun AccountType.labelRes() = when (this) {
    AccountType.CASH -> Res.string.account_creation_type_cash
    AccountType.BANK -> Res.string.account_creation_type_bank
    AccountType.CARD -> Res.string.account_creation_type_card
    AccountType.SAVINGS -> Res.string.account_creation_type_savings
}

@Preview
@Composable
private fun AccountsManageScreenViewPreview() {
    AppTheme {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = false,
                activeAccounts = PreviewActiveAccountsFull,
                archivedAccounts = PreviewArchivedAccounts,
                formattedTotal = "€11,575.32",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun AccountsManageScreenEditingPreview() {
    AppTheme {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = true,
                activeAccounts = PreviewActiveAccountsEditing,
                archivedAccounts = emptyList(),
                formattedTotal = "€11,395.32",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun AccountsManageEmptyPreview() {
    AppTheme {
        AccountsManageContent(
            state = AccountsManageState.Content(
                isEditing = false,
                activeAccounts = emptyList(),
                archivedAccounts = emptyList(),
                formattedTotal = null,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}
