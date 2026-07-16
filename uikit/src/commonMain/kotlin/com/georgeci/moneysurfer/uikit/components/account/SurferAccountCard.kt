package com.georgeci.moneysurfer.uikit.components.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

@Composable
fun SurferAccountCard(
    name: String,
    balance: String,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onAddClick: (() -> Unit)? = null,
    /** Accessibility label for the add button. Callers that provide [onAddClick] must pass a
     *  meaningful description (e.g. `stringResource(R.string.account_add_transaction)`). */
    addContentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .width(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.materialColors.primary)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = balance,
                style = AppTheme.typography.headlineSmall,
                color = Color.White,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = currency,
                style = AppTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = AppTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )

                if (onAddClick != null) {
                    IconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White,
                        ),
                    ) {
                        Icon(
                            imageVector = SurferIcons.Add,
                            contentDescription = addContentDescription,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SurferAccountCardPreview() {
    SurferComponentPreview {
        SurferAccountCard(
            name = "Bank Card",
            balance = "$16,567.00",
            currency = "USD",
            onClick = {},
            onAddClick = {},
            addContentDescription = "Add transaction",
        )
    }
}

@Preview
@Composable
private fun SurferAccountCardCashPreview() {
    SurferComponentPreview {
        SurferAccountCard(
            name = "Cash",
            balance = "$500.00",
            currency = "USD",
            onClick = {},
        )
    }
}
