package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_empty
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_from_account
import moneysurfer.feature.transaction.generated.resources.transaction_creation_from_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_rate_hint
import moneysurfer.feature.transaction.generated.resources.transaction_creation_swap_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_to_account
import moneysurfer.feature.transaction.generated.resources.transaction_creation_to_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferAccountsBlock(
    state: TransactionCreationState.Content,
    fromAmountState: TextFieldState,
    toAmountState: TextFieldState,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    val crossCurrency = state.crossCurrency
    val fromSymbol = currencySymbol(state.fromAccount?.currencyCode)
    val toSymbol = currencySymbol(state.toAccount?.currencyCode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransferLegCard(
            label = stringResource(Res.string.transaction_creation_from_label),
            currencySymbol = fromSymbol,
            amountText = fromAmountState.text.toString(),
            amountState = fromAmountState,
            account = state.fromAccount,
            accountPlaceholder = stringResource(Res.string.transaction_creation_from_account),
            onAccountClick = { onEvent(TransactionCreationEvent.OnOpenFromAccountChooser) },
        )

        state.amountError?.let { error ->
            AmountErrorText(error, modifier = Modifier.padding(start = 4.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primary)
                    .clickable { onEvent(TransactionCreationEvent.OnSwapAccountsClick) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.SwapHoriz,
                    contentDescription = stringResource(Res.string.transaction_creation_swap_content_description),
                    tint = AppTheme.materialColors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        TransferLegCard(
            label = stringResource(Res.string.transaction_creation_to_label),
            currencySymbol = toSymbol,
            amountText = if (crossCurrency) toAmountState.text.toString() else fromAmountState.text.toString(),
            amountState = if (crossCurrency) toAmountState else null,
            account = state.toAccount,
            accountPlaceholder = stringResource(Res.string.transaction_creation_to_account),
            onAccountClick = { onEvent(TransactionCreationEvent.OnOpenToAccountChooser) },
        )

        state.toAmountError?.let { error ->
            AmountErrorText(error, modifier = Modifier.padding(start = 4.dp))
        }

        if (crossCurrency) {
            RateHint(state = state, fromSymbol = fromSymbol, toSymbol = toSymbol)
        }
    }
}

@Composable
private fun RateHint(
    state: TransactionCreationState.Content,
    fromSymbol: String,
    toSymbol: String,
) {
    val from = state.amount.toDoubleOrNull()?.takeIf { it > 0 } ?: return
    val to = state.toAmount.toDoubleOrNull()?.takeIf { it > 0 } ?: return
    Text(
        text = stringResource(
            Res.string.transaction_creation_rate_hint,
            fromSymbol,
            formatRate(to / from),
            toSymbol,
        ),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferLegCard(
    label: String,
    currencySymbol: String,
    amountText: String,
    /** Non-null makes the leg editable; the receiving leg passes null unless the rate differs. */
    amountState: TextFieldState?,
    account: Account?,
    accountPlaceholder: String,
    onAccountClick: () -> Unit,
) {
    val shape = AppTheme.shapes.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.materialColors.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = currencySymbol,
                style = AppTheme.typography.headlineMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            if (amountState != null) {
                BasicTextField(
                    state = amountState,
                    inputTransformation = AmountInputTransformation,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.merge(AppTheme.typography.headlineLarge).copy(
                        color = AppTheme.materialColors.onSurface,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(AppTheme.materialColors.primary),
                )
            } else {
                Text(
                    text = amountText.ifEmpty { stringResource(Res.string.transaction_creation_amount_placeholder) },
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.materialColors.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        SurferPickerRow(
            label = accountPlaceholder,
            value = account?.let { acc ->
                val balance = MoneyFormatter.format(acc.balance, acc.currencyCode)
                "${acc.name} · $balance"
            } ?: stringResource(Res.string.transaction_creation_account_empty),
            icon = SurferIcons.CreditCard,
            onClick = onAccountClick,
        )
    }
}

private const val RATE_DECIMAL_SCALE = 10_000.0

private fun formatRate(rate: Double): String {
    val rounded = (rate * RATE_DECIMAL_SCALE).toLong() / RATE_DECIMAL_SCALE
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) {
        asLong.toString()
    } else {
        rounded.toString()
    }
}
