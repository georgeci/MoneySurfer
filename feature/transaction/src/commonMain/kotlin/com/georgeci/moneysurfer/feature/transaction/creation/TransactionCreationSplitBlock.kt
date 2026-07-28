package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_add_line
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_category_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_enable
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_over
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_remainder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_remove_line
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_title
import moneysurfer.feature.transaction.generated.resources.transaction_creation_split_total
import org.jetbrains.compose.resources.stringResource

/** Stable selectors for the split editor — see docs/testing/testing-strategy.md. */
object TransactionSplitTestTags {
    const val Toggle = "transactionCreation:split:toggle"
    const val AddLine = "transactionCreation:split:addLine"

    /** One line's amount field, suffixed with the line's index. */
    const val LineAmountPrefix = "transactionCreation:split:amount:"
}

/**
 * Turns the single-category form into a receipt filed under several categories, and back.
 *
 * Offered on creation only. Converting a stored transaction into a group would mean deleting the
 * row and writing N in its place — new ids, a different thing to sync — which is a larger change
 * than an edit screen should make behind one toggle.
 */
@Composable
internal fun SplitToggleRow(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    if (state.isEditMode) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        SurferButton(
            text = stringResource(Res.string.transaction_creation_split_enable),
            onClick = { onEvent(TransactionCreationEvent.OnSplitToggled) },
            style = if (state.isSplit) SurferButtonStyle.Tonal else SurferButtonStyle.Text,
            // Deliberately the smallest size: this row sits between the category grid and the note
            // field on a scrolling form, and every dp it adds pushes the note (and the Save button
            // the flows tap) further under the keyboard on a short screen.
            size = SurferButtonSize.Small,
            startIcon = SurferIcons.Category,
            modifier = Modifier.testTag(TransactionSplitTestTags.Toggle),
        )
    }
}

/**
 * The lines a split will be written as: a category and a slice of the amount each.
 *
 * The **last** line is not editable — it always carries whatever is left of the amount above. That
 * is what makes the legs add up by construction: there is no way to save a receipt whose parts do
 * not sum to the payment, and no arithmetic for the user to redo when they change one line.
 */
@Composable
internal fun SplitLinesBlock(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    val currency = state.selectedAccount?.currencyCode ?: CurrencyCode("USD")
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        Text(
            text = stringResource(Res.string.transaction_creation_split_title),
            style = AppTheme.typography.titleSmall,
            color = AppTheme.materialColors.onSurface,
        )
        state.splitLines.forEachIndexed { index, line ->
            SplitLineRow(
                line = line,
                index = index,
                // Only the trailing line is derived; every line above it is the user's to type.
                remainderFormatted = if (index == state.splitLines.lastIndex) {
                    MoneyFormatter.format(state.splitRemainder, currency)
                } else {
                    null
                },
                onEvent = onEvent,
            )
        }
        SurferButton(
            text = stringResource(Res.string.transaction_creation_split_add_line),
            onClick = { onEvent(TransactionCreationEvent.OnSplitLineAdded) },
            style = SurferButtonStyle.Text,
            size = SurferButtonSize.Biggest,
            startIcon = SurferIcons.Add,
            modifier = Modifier.testTag(TransactionSplitTestTags.AddLine),
        )
        SplitStatusLine(state = state, currency = currency)
    }
}

@Composable
private fun SplitLineRow(
    line: TransactionSplitLineUi,
    index: Int,
    remainderFormatted: String?,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurferPickerRow(
            label = stringResource(Res.string.transaction_creation_category_label),
            value = line.category?.name
                ?: stringResource(Res.string.transaction_creation_split_category_placeholder),
            icon = SurferIcons.Category,
            onClick = { onEvent(TransactionCreationEvent.OnOpenSplitLineCategoryChooser(line.key)) },
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = remainderFormatted ?: line.amount,
            onValueChange = { typed ->
                onEvent(TransactionCreationEvent.OnSplitLineAmountChanged(line.key, typed))
            },
            label = { Text(stringResource(Res.string.transaction_creation_amount_label)) },
            singleLine = true,
            readOnly = remainderFormatted != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = AppTheme.shapes.extraSmall,
            modifier = Modifier
                .width(SPLIT_AMOUNT_FIELD_WIDTH)
                .testTag(TransactionSplitTestTags.LineAmountPrefix + index),
        )
        IconButton(onClick = { onEvent(TransactionCreationEvent.OnSplitLineRemoved(line.key)) }) {
            Icon(
                imageVector = SurferIcons.Close,
                contentDescription = stringResource(Res.string.transaction_creation_split_remove_line),
                tint = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

/**
 * How the lines stand against the amount. Over-assigning is the only state the editor calls out in
 * error colour — a positive remainder is simply a line the user has not filled in yet.
 */
@Composable
private fun SplitStatusLine(state: TransactionCreationState.Content, currency: CurrencyCode) {
    val remainder = state.splitRemainder
    val overAssigned = remainder.isNegative()
    val text = when {
        overAssigned -> stringResource(
            Res.string.transaction_creation_split_over,
            MoneyFormatter.format(remainder.abs(), currency),
        )
        remainder.isZero() -> stringResource(
            Res.string.transaction_creation_split_total,
            MoneyFormatter.format(state.splitTotal, currency),
            MoneyFormatter.format(state.splitTotal, currency),
        )
        else -> stringResource(
            Res.string.transaction_creation_split_remainder,
            MoneyFormatter.format(remainder, currency),
        )
    }
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = if (overAssigned) {
            AppTheme.materialColors.error
        } else {
            AppTheme.materialColors.onSurfaceVariant
        },
    )
}

/** Wide enough for a five-figure amount without stealing the category picker's room. */
private val SPLIT_AMOUNT_FIELD_WIDTH = 132.dp
