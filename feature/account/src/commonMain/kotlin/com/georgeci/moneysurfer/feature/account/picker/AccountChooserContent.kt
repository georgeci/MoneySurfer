package com.georgeci.moneysurfer.feature.account.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferBottomSheetPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

internal data class AccountPickerRow(
    val id: AccountId,
    val name: String,
    val subtitle: String,
    val formattedBalance: String,
    val icon: ImageVector,
)

/**
 * Static content of the account-chooser bottom sheet. Renders the drag handle, header, total
 * summary, the picker list, the dashed "Add new account" CTA and the transfer footer. The hosting
 * [Composable] provides the surrounding `ModalBottomSheet` chrome and wires events through a
 * ViewModel.
 *
 * [transferInsteadLabel] is null when the host has no transfer to offer — picking the second leg
 * of a transfer, for instance, is already a transfer.
 */
@Suppress("LongParameterList")
@Composable
internal fun AccountChooserContent(
    title: String,
    summary: String,
    accounts: List<AccountPickerRow>,
    selectedId: AccountId?,
    addNewLabel: String,
    addNewSubtitle: String,
    onSelect: (AccountId) -> Unit,
    onAddNew: () -> Unit,
    onClose: () -> Unit,
    transferInsteadLabel: String? = null,
    onTransferInstead: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Close,
                    contentDescription = null,
                    tint = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            text = summary,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            accounts.forEach { row ->
                AccountRow(
                    row = row,
                    selected = row.id == selectedId,
                    onClick = { onSelect(row.id) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        SurferActionCard(
            modifier = Modifier.padding(horizontal = 12.dp),
            onClick = onAddNew,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, AppTheme.materialColors.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SurferIcons.Add,
                        contentDescription = null,
                        tint = AppTheme.materialColors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = addNewLabel,
                        style = AppTheme.typography.titleSmall,
                        color = AppTheme.materialColors.primary,
                    )
                    Text(
                        text = addNewSubtitle,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
        }

        if (transferInsteadLabel != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTransferInstead)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = SurferIcons.SwapHoriz,
                    contentDescription = null,
                    tint = AppTheme.materialColors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = transferInsteadLabel,
                    style = AppTheme.typography.labelLarge,
                    color = AppTheme.materialColors.primary,
                )
            }
        }
    }
}

@Composable
private fun AccountRow(
    row: AccountPickerRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SurferCard(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = row.icon,
                    contentDescription = null,
                    tint = AppTheme.materialColors.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.subtitle.isNotEmpty()) {
                    Text(
                        text = row.subtitle,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SurferSplitAmount(
                formattedAmount = row.formattedBalance,
                tier = SurferSplitAmountTier.Body,
                color = AppTheme.materialColors.onSurface,
                signAlpha = 0.6f,
                fractionAlpha = 0.5f,
            )
            Spacer(Modifier.size(4.dp))
            SelectionRadio(checked = selected)
        }
    }
}

@Composable
private fun SelectionRadio(checked: Boolean) {
    val borderColor =
        if (checked) AppTheme.materialColors.primary else AppTheme.materialColors.outline
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primary),
            )
        }
    }
}

private val previewRows = listOf(
    AccountPickerRow(
        id = AccountId("preview-acc-1"),
        name = "Everyday",
        subtitle = "EUR",
        formattedBalance = "€2,480.32",
        icon = SurferIcons.CreditCard,
    ),
    AccountPickerRow(
        id = AccountId("preview-acc-2"),
        name = "Emergency Fund",
        subtitle = "EUR",
        formattedBalance = "€8,915.00",
        icon = SurferIcons.Savings,
    ),
    AccountPickerRow(
        id = AccountId("preview-acc-3"),
        name = "Cash wallet",
        subtitle = "EUR",
        formattedBalance = "€180.00",
        icon = SurferIcons.Cash,
    ),
)

@Preview
@Composable
private fun AccountChooserContentPreview() {
    SurferBottomSheetPreview {
        AccountChooserContent(
            title = "From account",
            summary = "Total across 3 accounts · €11,575.32",
            accounts = previewRows,
            selectedId = AccountId("preview-acc-1"),
            addNewLabel = "Add new account",
            addNewSubtitle = "Bank, savings, cash or card",
            onSelect = {},
            onAddNew = {},
            onClose = {},
            transferInsteadLabel = "Transfer between accounts instead",
        )
    }
}

@Preview
@Composable
private fun AccountChooserContentEmptyPreview() {
    SurferBottomSheetPreview {
        AccountChooserContent(
            title = "From account",
            summary = "No accounts yet",
            accounts = emptyList(),
            selectedId = null,
            addNewLabel = "Add new account",
            addNewSubtitle = "Bank, savings, cash or card",
            onSelect = {},
            onAddNew = {},
            onClose = {},
        )
    }
}
