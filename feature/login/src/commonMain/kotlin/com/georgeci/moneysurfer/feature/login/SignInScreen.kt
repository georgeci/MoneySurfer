package com.georgeci.moneysurfer.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.SurferFullScreenLoader
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.login.generated.resources.Res
import moneysurfer.feature.login.generated.resources.sign_in_anonymous
import moneysurfer.feature.login.generated.resources.sign_in_brand
import moneysurfer.feature.login.generated.resources.sign_in_demo_mode
import moneysurfer.feature.login.generated.resources.sign_in_email_label
import moneysurfer.feature.login.generated.resources.sign_in_error_email_in_use
import moneysurfer.feature.login.generated.resources.sign_in_error_invalid_credentials
import moneysurfer.feature.login.generated.resources.sign_in_error_missing_credentials
import moneysurfer.feature.login.generated.resources.sign_in_error_permission_denied
import moneysurfer.feature.login.generated.resources.sign_in_error_unknown
import moneysurfer.feature.login.generated.resources.sign_in_error_weak_password
import moneysurfer.feature.login.generated.resources.sign_in_hero_subtitle
import moneysurfer.feature.login.generated.resources.sign_in_hero_title
import moneysurfer.feature.login.generated.resources.sign_in_password_label
import moneysurfer.feature.login.generated.resources.sign_in_submit_signin
import moneysurfer.feature.login.generated.resources.sign_in_submit_signup
import moneysurfer.feature.login.generated.resources.sign_in_terms
import moneysurfer.feature.login.generated.resources.sign_in_toggle_to_signin
import moneysurfer.feature.login.generated.resources.sign_in_toggle_to_signup
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private object SignInLayout {
    val donutOffsetX = 80.dp
    val donutOffsetY = 120.dp
    val donutSize = 280.dp
    val brandIconSize = 44.dp
    val brandIconCornerRadius = 14.dp
    val brandIconContentSize = 24.dp
    const val donutHoleRadiusFraction = 160f / 280f
    const val donutHoleOffsetXFraction = 100f / 280f
    const val donutHoleOffsetYFraction = 60f / 280f
}

@Composable
private fun SignInError.localized(): String = stringResource(
    when (this) {
        SignInError.MissingCredentials -> Res.string.sign_in_error_missing_credentials
        SignInError.InvalidCredentials -> Res.string.sign_in_error_invalid_credentials
        SignInError.EmailAlreadyInUse -> Res.string.sign_in_error_email_in_use
        SignInError.WeakPassword -> Res.string.sign_in_error_weak_password
        SignInError.PermissionDenied -> Res.string.sign_in_error_permission_denied
        SignInError.Unknown -> Res.string.sign_in_error_unknown
    },
)

@Composable
fun SignInScreen(
    onNavigateToWorkspaceSelector: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            SignInEffect.NavigateToWorkspaceSelector -> onNavigateToWorkspaceSelector()
        }
    }

    SignInContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun SignInContent(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .surferSafeInsets(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            DecorativeDonut(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = SignInLayout.donutOffsetX, y = SignInLayout.donutOffsetY)
                    .size(SignInLayout.donutSize),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        PaddingValues(
                            start = AppTheme.spacing.large,
                            end = AppTheme.spacing.large,
                            top = AppTheme.spacing.xxxLarge,
                            bottom = AppTheme.spacing.xLarge,
                        ),
                    ),
            ) {
                BrandMark()

                Spacer(Modifier.weight(1f))

                Text(
                    text = stringResource(Res.string.sign_in_hero_title),
                    style = AppTheme.typography.displaySmall,
                    color = AppTheme.materialColors.onSurface,
                )
                Spacer(Modifier.height(AppTheme.spacing.medium))
                Text(
                    text = stringResource(Res.string.sign_in_hero_subtitle),
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )

                Spacer(Modifier.height(AppTheme.spacing.large))

                EmailPasswordForm(state = state, onEvent = onEvent)

                if (state.error != null) {
                    Spacer(Modifier.height(AppTheme.spacing.small))
                    Text(
                        text = state.error.localized(),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(AppTheme.spacing.medium))

                SurferButton(
                    text = stringResource(
                        when (state.mode) {
                            AuthMode.SignIn -> Res.string.sign_in_submit_signin
                            AuthMode.SignUp -> Res.string.sign_in_submit_signup
                        },
                    ),
                    onClick = { onEvent(SignInEvent.OnSubmitClick) },
                    modifier = Modifier.fillMaxWidth(),
                    size = SurferButtonSize.Biggest,
                    enabled = state.canSubmit,
                )

                Spacer(Modifier.height(AppTheme.spacing.small))

                SurferButton(
                    text = stringResource(
                        when (state.mode) {
                            AuthMode.SignIn -> Res.string.sign_in_toggle_to_signup
                            AuthMode.SignUp -> Res.string.sign_in_toggle_to_signin
                        },
                    ),
                    onClick = { onEvent(SignInEvent.OnToggleModeClick) },
                    modifier = Modifier.fillMaxWidth(),
                    style = SurferButtonStyle.Text,
                    enabled = !state.isLoading,
                )

                Spacer(Modifier.height(AppTheme.spacing.large))

                SurferButton(
                    text = stringResource(Res.string.sign_in_anonymous),
                    onClick = { onEvent(SignInEvent.OnAnonymousLoginClick) },
                    modifier = Modifier.fillMaxWidth(),
                    style = SurferButtonStyle.Outlined,
                    enabled = !state.isLoading,
                    startIcon = SurferIcons.Fingerprint,
                )

                Spacer(Modifier.height(AppTheme.spacing.small))

                SurferButton(
                    text = stringResource(Res.string.sign_in_demo_mode),
                    onClick = { onEvent(SignInEvent.OnLoginClick) },
                    modifier = Modifier.fillMaxWidth(),
                    style = SurferButtonStyle.Text,
                    enabled = !state.isLoading,
                )

                Spacer(Modifier.height(AppTheme.spacing.large))

                Text(
                    text = stringResource(Res.string.sign_in_terms),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.isLoading) {
                SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun EmailPasswordForm(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    OutlinedTextField(
        value = state.email,
        onValueChange = { onEvent(SignInEvent.OnEmailChanged(it)) },
        label = { Text(stringResource(Res.string.sign_in_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(AppTheme.spacing.small))

    OutlinedTextField(
        value = state.password,
        onValueChange = { onEvent(SignInEvent.OnPasswordChanged(it)) },
        label = { Text(stringResource(Res.string.sign_in_password_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        visualTransformation = PasswordVisualTransformation(),
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(SignInLayout.brandIconSize)
                .clip(RoundedCornerShape(SignInLayout.brandIconCornerRadius))
                .background(AppTheme.materialColors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Wallet,
                contentDescription = null,
                tint = AppTheme.materialColors.onPrimaryContainer,
                modifier = Modifier.size(SignInLayout.brandIconContentSize),
            )
        }
        Text(
            text = stringResource(Res.string.sign_in_brand),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Composable
private fun DecorativeDonut(modifier: Modifier = Modifier) {
    val primary = AppTheme.materialColors.primary
    val tertiary = AppTheme.materialColors.tertiary
    val container = AppTheme.materialColors.secondaryContainer
    val surface = AppTheme.materialColors.surface

    val isDark = AppTheme.materialColors.background.luminance() < 0.5f
    val donutAlpha = if (isDark) 0.35f else 0.18f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 2f
            val sweepBrush = Brush.sweepGradient(
                colors = listOf(primary, primary, tertiary, tertiary, container, container, primary),
                center = Offset(cx, cy),
            )
            drawCircle(
                brush = sweepBrush,
                radius = radius,
                center = Offset(cx, cy),
                alpha = donutAlpha,
            )
            drawCircle(
                color = surface,
                radius = radius * SignInLayout.donutHoleRadiusFraction,
                center = Offset(
                    x = cx + radius * SignInLayout.donutHoleOffsetXFraction,
                    y = cy + radius * SignInLayout.donutHoleOffsetYFraction,
                ),
            )
        }
    }
}

@Preview
@Composable
private fun SignInScreenPreview() {
    AppTheme {
        SignInContent(
            state = SignInState(),
            onEvent = {},
        )
    }
}
