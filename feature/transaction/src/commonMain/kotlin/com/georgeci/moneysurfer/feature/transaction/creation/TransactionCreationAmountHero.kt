package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_error_format
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_error_zero
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_expense
import moneysurfer.feature.transaction.generated.resources.transaction_creation_income
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Opacity of the type pill's tint wash. See [TypePill] for why it is composited, not blended. */
private const val TYPE_PILL_WASH_ALPHA = 0.18f

@Composable
internal fun AmountHero(
    type: TransactionTypeUi,
    currencySymbol: String,
    amountState: TextFieldState,
    error: TransactionAmountError?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TypePill(type = type)
        Spacer(Modifier.height(AppTheme.spacing.xSmall))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = currencySymbol,
                style = AppTheme.typography.displayMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            val text = amountState.text.toString()
            val placeholderText = stringResource(Res.string.transaction_creation_amount_placeholder)
            BasicTextField(
                state = amountState,
                modifier = Modifier.testTag(TransactionCreationTestTags.Amount),
                inputTransformation = AmountInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.merge(AppTheme.typography.displayLarge).copy(
                    color = AppTheme.materialColors.onSurface,
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(AppTheme.materialColors.primary),
                decorator = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholderText,
                                style = AppTheme.typography.displayLarge,
                                color = AppTheme.materialColors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(AppTheme.spacing.xSmall))
            AmountErrorText(error)
        }
    }
}

@Composable
internal fun AmountErrorText(
    error: TransactionAmountError,
    modifier: Modifier = Modifier,
) {
    val messageRes: StringResource = when (error) {
        TransactionAmountError.INVALID_FORMAT -> Res.string.transaction_creation_amount_error_format
        TransactionAmountError.NOT_POSITIVE -> Res.string.transaction_creation_amount_error_zero
    }
    Text(
        text = stringResource(messageRes),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.materialColors.error,
        modifier = modifier,
    )
}

@Composable
private fun TypePill(type: TransactionTypeUi) {
    val tint = typeTint(type)
    val labelRes = when (type) {
        TransactionTypeUi.Expense -> Res.string.transaction_creation_expense
        TransactionTypeUi.Income -> Res.string.transaction_creation_income
        TransactionTypeUi.Transfer -> Res.string.transaction_creation_transfer
    }
    // Composited over `surface` rather than left translucent: the label is `tint` on a wash
    // of the same tint, so the contrast ratio depends entirely on what shows through. Pinning
    // the wash to the scaffold's own colour keeps the pair at a known ≥4.5:1 even if the pill
    // is later moved onto a card.
    val wash = tint.copy(alpha = TYPE_PILL_WASH_ALPHA).compositeOver(AppTheme.materialColors.surface)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(wash)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = AppTheme.typography.labelMedium,
            color = tint,
        )
    }
}

internal fun currencySymbol(code: CurrencyCode?): String {
    val value = code?.value ?: return "$"
    return when (value) {
        "EUR" -> "€"
        "GBP" -> "£"
        "PLN" -> "zł"
        "JPY" -> "¥"
        "RUB" -> "₽"
        else -> "$"
    }
}
