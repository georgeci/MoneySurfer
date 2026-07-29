package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_password_hide
import moneysurfer.uikit.generated.resources.uikit_password_show
import org.jetbrains.compose.resources.stringResource

/**
 * Outlined form field for the app's data-entry screens.
 *
 * Three behaviours are the reason this exists as a shared component rather than a raw
 * [OutlinedTextField] per screen:
 *
 *  - **Errors are attached to the field.** Pass [errorText] and the message renders right under
 *    the input, with the error outline — instead of a detached line elsewhere on the form that
 *    leaves the user hunting for which input is wrong.
 *  - **The message line never moves the form.** The line under the field is always laid out, even
 *    with nothing to say, so an error appearing does not push the next field and the buttons down
 *    the screen. It is drawn here rather than through [OutlinedTextField]'s `supportingText` slot
 *    for the same reason the slot is not used for [helperText]: M3 indents supporting text by
 *    16dp, which reads as a stray offset under a field the design aligns flush.
 *  - **The keyboard never covers the focused field.** While focused, the field re-requests
 *    [BringIntoViewRequester.bringIntoView] every time the IME inset changes, so it scrolls clear
 *    of the keyboard as it animates in. The host still has to be scrollable and IME-padded for
 *    this to have anywhere to scroll to.
 *
 * [helperText] is the always-on hint for the field; [errorText] replaces it while it is set.
 * [fieldTestTag] tags the input; the message is tagged `"<fieldTestTag>:error"` so a UI test can
 * assert it without matching on localized copy.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurferTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    helperText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    prefix: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    fieldTestTag: String? = null,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    // Recomposes on every frame of the IME animation, which is exactly what re-triggers the
    // scroll below — a single bringIntoView() on focus fires before the keyboard has taken up
    // its space and lands the field back underneath it.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(focused, imeBottom, errorText) {
        if (focused) bringIntoView.bringIntoView()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            enabled = enabled,
            isError = errorText != null,
            prefix = prefix,
            trailingIcon = trailingIcon,
            shape = AppTheme.shapes.large,
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged { focused = it.isFocused }
                // The message is drawn below rather than through M3's `supportingText`, and that
                // slot is also what put the text into the field's semantics. Without this a screen
                // reader announces the field as invalid and never says why.
                .then(errorText?.let { message -> Modifier.semantics { error(message) } } ?: Modifier)
                .then(fieldTestTag?.let { Modifier.testTag(it) } ?: Modifier),
        )
        SurferFieldMessage(
            text = errorText ?: helperText,
            isError = errorText != null,
            modifier = fieldTestTag?.let { Modifier.testTag("$it:error") } ?: Modifier,
        )
    }
}

/**
 * The one-line message slot under a form field: the error, else the hint, else nothing visible.
 *
 * Always occupies its line — one line of `bodySmall` plus the gap above it — so a field that
 * starts clean and later fails validation does not shift everything below it. The line is measured
 * from the style rather than hard-coded: `bodySmall` is sized in `sp`, so at a system font scale
 * above 1.0 a fixed dp reserve would be shorter than the text it has to hold and the shift would
 * come back on exactly the devices that can least afford it.
 *
 * Public so screens with a control that is not a [SurferTextField] (a picker, a segmented row)
 * can reserve the same line under it.
 */
@Composable
fun SurferFieldMessage(
    text: String?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val lineHeight = AppTheme.typography.bodySmall.lineHeight
    val reserved = with(LocalDensity.current) {
        if (lineHeight.type == TextUnitType.Sp) lineHeight.toDp() else FallbackLineHeight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = reserved + MessageTopPadding)
            .padding(top = MessageTopPadding),
    ) {
        if (text != null) {
            Text(
                text = text,
                style = AppTheme.typography.bodySmall,
                color = if (isError) {
                    AppTheme.materialColors.error
                } else {
                    AppTheme.materialColors.onSurfaceVariant
                },
            )
        }
    }
}

private val MessageTopPadding: Dp = 4.dp

/** Only reached if the theme ever gives `bodySmall` an em-based line height. */
private val FallbackLineHeight: Dp = 16.dp

/**
 * [SurferTextField] pre-wired for password entry: password keyboard, masked text, and a trailing
 * reveal toggle. The reveal state is [rememberSaveable] so rotating the device does not silently
 * re-mask a password the user chose to check.
 */
@Composable
fun SurferPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    fieldTestTag: String? = null,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }

    SurferTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        errorText = errorText,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(
                onClick = { revealed = !revealed },
                enabled = enabled,
                modifier = fieldTestTag?.let { Modifier.testTag("$it:reveal") } ?: Modifier,
            ) {
                Icon(
                    imageVector = if (revealed) SurferIcons.VisibilityOff else SurferIcons.Visibility,
                    contentDescription = stringResource(
                        if (revealed) Res.string.uikit_password_hide else Res.string.uikit_password_show,
                    ),
                )
            }
        },
        colors = colors,
        fieldTestTag = fieldTestTag,
    )
}
