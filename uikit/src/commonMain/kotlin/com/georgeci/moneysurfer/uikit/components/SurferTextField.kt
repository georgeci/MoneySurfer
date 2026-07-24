package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_password_hide
import moneysurfer.uikit.generated.resources.uikit_password_show
import org.jetbrains.compose.resources.stringResource

/** Corner radius shared by every Surfer form field. */
val SurferFieldCorner: Dp = 14.dp

/**
 * Outlined form field for the app's data-entry screens.
 *
 * Two behaviours are the reason this exists as a shared component rather than a raw
 * [OutlinedTextField] per screen:
 *
 *  - **Errors are attached to the field.** Pass [errorText] and the message renders as supporting
 *    text right under the input, with the error outline — instead of a detached line elsewhere on
 *    the form that leaves the user hunting for which input is wrong.
 *  - **The keyboard never covers the focused field.** While focused, the field re-requests
 *    [BringIntoViewRequester.bringIntoView] every time the IME inset changes, so it scrolls clear
 *    of the keyboard as it animates in. The host still has to be scrollable and IME-padded for
 *    this to have anywhere to scroll to.
 *
 * [fieldTestTag] tags the input; the supporting text is tagged `"<fieldTestTag>:error"` so a UI
 * test can assert the message without matching on localized copy.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurferTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
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

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        enabled = enabled,
        isError = errorText != null,
        trailingIcon = trailingIcon,
        supportingText = errorText?.let {
            {
                Text(
                    text = it,
                    modifier = fieldTestTag?.let { tag -> Modifier.testTag("$tag:error") } ?: Modifier,
                )
            }
        },
        shape = RoundedCornerShape(SurferFieldCorner),
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { focused = it.isFocused }
            .then(fieldTestTag?.let { Modifier.testTag(it) } ?: Modifier),
    )
}

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
