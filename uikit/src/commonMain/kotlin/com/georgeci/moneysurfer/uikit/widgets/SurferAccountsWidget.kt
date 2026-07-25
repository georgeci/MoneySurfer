package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferActionCard
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountManageCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferDashedBorder
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferAccountItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val balance: String,
    val icon: ImageVector = SurferIcons.Wallet,
)

/**
 * The empty-state "add your first account" affordance: its copy, its click, and the selector a
 * UI test taps it by. Four arguments describing one control, so they travel as one — the host
 * screen still owns [testTag], because the CTA is a step in its flow rather than the widget's.
 */
data class SurferAddAccountCta(
    val label: String,
    val onClick: () -> Unit,
    val trailingLabel: String? = null,
    val testTag: String? = null,
)

/** Accounts widget for the dashboard column. */
@Composable
fun SurferAccountsWidget(
    items: List<SurferAccountItem>,
    addCta: SurferAddAccountCta,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    onItemClick: ((SurferAccountItem) -> Unit)? = null,
) {
    val hero = size == SurferWidgetSize.Hero
    val visibleItems = if (hero) items else items.take(2)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleItems.forEach { item ->
            SurferAccountManageCard(
                name = item.name,
                subtitle = item.subtitle,
                icon = item.icon,
                formattedBalance = item.balance,
                onClick = onItemClick?.let { handler -> { handler(item) } },
            )
        }
        // The add-account CTA is the empty-state affordance only; once accounts exist,
        // new ones are created from the "Manage" flow.
        if (items.isEmpty()) {
            AddAccountRow(
                label = addCta.label,
                trailingLabel = addCta.trailingLabel,
                onClick = addCta.onClick,
                modifier = addCta.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

@Composable
private fun AddAccountRow(
    label: String,
    trailingLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferActionCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .surferDashedBorder(color = AppTheme.materialColors.outline, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Add,
                    contentDescription = SurferSemantics.Decorative,
                    tint = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (trailingLabel != null) {
                Text(
                    text = trailingLabel,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.primary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferAccountsWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferAccountsWidget(
                items = listOf(
                    SurferAccountItem("1", "Everyday", "Current · •• 4021", "€2,480.32", SurferIcons.CreditCard),
                    SurferAccountItem("2", "Emergency Fund", "Savings · •• 7712", "€8,915.00", SurferIcons.Savings),
                    SurferAccountItem("3", "Cash wallet", "Cash", "€180.00", SurferIcons.Cash),
                ),
                addCta = SurferAddAccountCta(
                    label = "Add account",
                    onClick = {},
                    trailingLabel = "New",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .width(360.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferAccountsWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferAccountsWidget(
                items = emptyList(),
                addCta = SurferAddAccountCta(
                    label = "Add account",
                    onClick = {},
                    trailingLabel = "New",
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
