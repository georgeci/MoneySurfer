package com.georgeci.moneysurfer.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.SurferAppIcon
import com.georgeci.moneysurfer.uikit.components.SurferAuthBackground
import com.georgeci.moneysurfer.uikit.components.SurferAuthGradient
import com.georgeci.moneysurfer.uikit.components.SurferFullScreenLoader
import com.georgeci.moneysurfer.uikit.components.SurferPasswordField
import com.georgeci.moneysurfer.uikit.components.SurferTextField
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.ConfigureSystemBars
import com.georgeci.moneysurfer.uikit.tokens.AuthColors
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.login.generated.resources.Res
import moneysurfer.feature.login.generated.resources.sign_in_anonymous
import moneysurfer.feature.login.generated.resources.sign_in_brand
import moneysurfer.feature.login.generated.resources.sign_in_demo_mode
import moneysurfer.feature.login.generated.resources.sign_in_email_label
import moneysurfer.feature.login.generated.resources.sign_in_error_dialog_ok
import moneysurfer.feature.login.generated.resources.sign_in_error_dialog_title
import moneysurfer.feature.login.generated.resources.sign_in_error_email_in_use
import moneysurfer.feature.login.generated.resources.sign_in_error_email_invalid
import moneysurfer.feature.login.generated.resources.sign_in_error_email_required
import moneysurfer.feature.login.generated.resources.sign_in_error_invalid_credentials
import moneysurfer.feature.login.generated.resources.sign_in_error_password_required
import moneysurfer.feature.login.generated.resources.sign_in_error_password_too_short
import moneysurfer.feature.login.generated.resources.sign_in_error_permission_denied
import moneysurfer.feature.login.generated.resources.sign_in_error_unknown
import moneysurfer.feature.login.generated.resources.sign_in_error_weak_password
import moneysurfer.feature.login.generated.resources.sign_in_hero_subtitle
import moneysurfer.feature.login.generated.resources.sign_in_hero_title
import moneysurfer.feature.login.generated.resources.sign_in_or
import moneysurfer.feature.login.generated.resources.sign_in_password_label
import moneysurfer.feature.login.generated.resources.sign_in_sheet_subtitle
import moneysurfer.feature.login.generated.resources.sign_in_sheet_title
import moneysurfer.feature.login.generated.resources.sign_in_submit_signin
import moneysurfer.feature.login.generated.resources.sign_in_submit_signup
import moneysurfer.feature.login.generated.resources.sign_in_terms
import moneysurfer.feature.login.generated.resources.sign_in_toggle_to_signin
import moneysurfer.feature.login.generated.resources.sign_in_toggle_to_signup
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

object SignInTestTags {
    const val Root = "signIn:root"
    const val BrandHeader = "signIn:brandHeader"
    const val HeroTitle = "signIn:heroTitle"
    const val HeroSubtitle = "signIn:heroSubtitle"
    const val Sheet = "signIn:sheet"
    const val SheetTitle = "signIn:sheetTitle"
    const val SheetSubtitle = "signIn:sheetSubtitle"
    const val EmailField = "signIn:email"
    const val PasswordField = "signIn:password"
    const val SubmitButton = "signIn:submit"
    const val ToggleModeButton = "signIn:toggleMode"
    const val AnonymousButton = "signIn:anonymous"
    const val DemoButton = "signIn:demo"
    const val ErrorText = "signIn:error"
    const val ErrorDialog = "signIn:errorDialog"
    const val ErrorDialogConfirm = "signIn:errorDialog:confirm"
    const val EmailError = "$EmailField:error"
    const val PasswordError = "$PasswordField:error"
    const val PasswordReveal = "$PasswordField:reveal"
    const val Loader = "signIn:loader"
    const val Terms = "signIn:terms"
}

private val SheetCorner: Dp = 28.dp
private val PrimaryButtonHeight: Dp = 52.dp
private val BrandIconSize: Dp = 42.dp
private val HeroTitleSize = 40.sp
private val HeroTitleLineHeight = 44.sp
private val HeroSubtitleMaxWidth: Dp = 320.dp
private val SheetPadding: Dp = 20.dp
private val SheetElevation: Dp = 12.dp
private val SheetTitleSize = 18.sp
private val PasskeyIconSize: Dp = 18.dp
private val OrLabelSize = 11.sp
private val PrimaryLabelSize = 15.sp
private val ContentMaxWidth: Dp = 480.dp

// ── Split (wide + landscape) layout ──────────────────────────────────────────────────────────────
private val SplitMaxWidth: Dp = 1120.dp
private val SplitColumnGap: Dp = 40.dp
private val SplitSheetMaxWidth: Dp = 432.dp
private val SplitBrandIconSize: Dp = 44.dp
private val SplitHeroTitleSize = 52.sp
private val SplitHeroTitleLineHeight = 54.sp
private val SplitHeroSubtitleMaxWidth: Dp = 380.dp
private const val SplitBrandWeight = 0.54f
private const val SplitFormWeight = 0.46f

@Composable
private fun SignInError.localized(): String = stringResource(
    when (this) {
        SignInError.EmailRequired -> Res.string.sign_in_error_email_required
        SignInError.EmailInvalid -> Res.string.sign_in_error_email_invalid
        SignInError.PasswordRequired -> Res.string.sign_in_error_password_required
        SignInError.PasswordTooShort -> Res.string.sign_in_error_password_too_short
        SignInError.InvalidCredentials -> Res.string.sign_in_error_invalid_credentials
        SignInError.EmailAlreadyInUse -> Res.string.sign_in_error_email_in_use
        SignInError.WeakPassword -> Res.string.sign_in_error_weak_password
        SignInError.PermissionDenied -> Res.string.sign_in_error_permission_denied
        SignInError.Unknown -> Res.string.sign_in_error_unknown
    },
)

@Composable
fun SignInScreen(
    onNavigateToWorkspaceSelector: (cloudDataUnavailable: Boolean) -> Unit,
    onNavigateToLegal: () -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            is SignInEffect.NavigateToWorkspaceSelector ->
                onNavigateToWorkspaceSelector(effect.cloudDataUnavailable)
            SignInEffect.NavigateToLegal -> onNavigateToLegal()
        }
    }

    SignInContent(
        state = state,
        onEvent = viewModel::onEvent,
    )
}

/**
 * Stateless body of the sign-in screen. Public so screen-state UI tests can mount it with an
 * injected [SignInState] instead of standing up a Koin-backed [SignInViewModel].
 */
@Composable
fun SignInContent(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    // The brand backdrop is drawn edge to edge, under the status bar and the navigation bar, and
    // only the content is inset — that is what tints the system bars green. The gradient is light
    // at the top and the wave band is dark at the bottom, so the two bars get opposite icon tints.
    ConfigureSystemBars(darkStatusBarBackground = false, darkNavigationBarBackground = true)
    // Measured on the window rather than inside the insets below, so opening the keyboard — which
    // eats into the bottom inset — cannot flip the screen between layouts mid-typing.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .surferTestTagAsId()
            .testTag(SignInTestTags.Root),
    ) {
        val split = isSplitLayout(width = maxWidth, height = maxHeight)

        SurferAuthBackground(
            modifier = Modifier.fillMaxSize(),
            gradient = if (split) SurferAuthGradient.Diagonal else SurferAuthGradient.Vertical,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .surferSafeInsets()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            if (split) {
                SignInSplitLayout(state = state, onEvent = onEvent)
            } else {
                SignInStackedLayout(state = state, onEvent = onEvent)
            }
        }

        if (state.isLoading) {
            SurferFullScreenLoader(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SignInTestTags.Loader),
            )
        }

        state.dialogError?.let { error ->
            SignInErrorDialog(
                error = error,
                onDismiss = { onEvent(SignInEvent.OnErrorDismiss) },
            )
        }
    }
}

/**
 * Whether the brand block and the sheet sit side by side instead of stacked.
 *
 * Two conditions, both needed. [SurferWindowSize.Expanded] is the width at which two columns each
 * keep a readable measure — below it (a tablet in portrait, the 834 dp artboard) the design still
 * stacks. And the window has to be wider than it is tall: a 1024 dp tablet held in *portrait* is
 * Expanded too, and splitting it would leave two tall, half-empty columns.
 */
private fun isSplitLayout(width: Dp, height: Dp): Boolean =
    SurferWindowSize.ofWidth(width) >= SurferWindowSize.Expanded && width > height

/**
 * Phone and tablet-portrait: brand on top, sheet pinned to the bottom.
 *
 * The bottom safe-drawing inset carries the IME, so the insets applied by the caller already lift
 * the content clear of the keyboard. What was still missing is somewhere to lift it TO: with a
 * fixed-height column the sheet was squeezed off-screen and the "Create account" button became
 * unreachable. Measuring inside the inset padding yields a viewport height that already accounts
 * for the keyboard, and the scroll container covers the rest.
 */
@Composable
private fun SignInStackedLayout(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(
                    start = AppTheme.spacing.default,
                    end = AppTheme.spacing.default,
                    top = AppTheme.spacing.xLarge,
                    bottom = AppTheme.spacing.large,
                ),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Exactly two children so SpaceBetween reproduces the pinned-to-bottom sheet of the
            // original weight(1f) layout, which a scrollable column cannot use.
            Column(modifier = Modifier.padding(bottom = AppTheme.spacing.large)) {
                SignInBrandHeader()
                Spacer(Modifier.height(AppTheme.spacing.xLarge))
                SignInHero()
            }
            SignInActionSheet(state = state, onEvent = onEvent)
        }
    }
}

/**
 * Desktop and tablet-landscape: brand on the left, the sheet as a card on the right, both centred
 * on the vertical axis. Stacking these would put the sheet below the fold on every landscape
 * window, and the wave band — drawn along the bottom of the backdrop — would end up behind the
 * card rather than beside it.
 */
@Composable
private fun SignInSplitLayout(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .surferContentContainer(maxWidth = SplitMaxWidth)
            .padding(
                horizontal = AppTheme.spacing.xLarge,
                vertical = AppTheme.spacing.large,
            ),
        horizontalArrangement = Arrangement.spacedBy(SplitColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(SplitBrandWeight)) {
            SignInBrandHeader(iconSize = SplitBrandIconSize)
            Spacer(Modifier.height(AppTheme.spacing.xxLarge))
            SignInHero(
                titleSize = SplitHeroTitleSize,
                titleLineHeight = SplitHeroTitleLineHeight,
                subtitleMaxWidth = SplitHeroSubtitleMaxWidth,
            )
        }
        // The sheet scrolls on its own: the brand block always fits, but the form is taller than a
        // short landscape window (a phone on its side is ~410 dp tall) and has to stay reachable.
        BoxWithConstraints(
            modifier = Modifier
                .weight(SplitFormWeight)
                .fillMaxHeight(),
        ) {
            val viewportHeight = maxHeight
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight)
                    .widthIn(max = SplitSheetMaxWidth),
                verticalArrangement = Arrangement.Center,
            ) {
                SignInActionSheet(state = state, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun SignInErrorDialog(
    error: SignInError,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.sign_in_error_dialog_title)) },
        text = { Text(error.localized()) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(SignInTestTags.ErrorDialogConfirm),
            ) {
                Text(stringResource(Res.string.sign_in_error_dialog_ok))
            }
        },
        containerColor = AppTheme.materialColors.surface,
        modifier = Modifier.testTag(SignInTestTags.ErrorDialog),
    )
}

@Composable
private fun SignInBrandHeader(
    modifier: Modifier = Modifier,
    iconSize: Dp = BrandIconSize,
) {
    Row(
        modifier = modifier.testTag(SignInTestTags.BrandHeader),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        SurferAppIcon(size = iconSize)
        Text(
            text = stringResource(Res.string.sign_in_brand),
            style = AppTheme.typography.titleLarge,
            color = AuthColors.OnBrand,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SignInHero(
    modifier: Modifier = Modifier,
    titleSize: TextUnit = HeroTitleSize,
    titleLineHeight: TextUnit = HeroTitleLineHeight,
    subtitleMaxWidth: Dp = HeroSubtitleMaxWidth,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.sign_in_hero_title),
            color = AuthColors.OnBrand,
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.testTag(SignInTestTags.HeroTitle),
        )
        Spacer(Modifier.height(AppTheme.spacing.default))
        Text(
            text = stringResource(Res.string.sign_in_hero_subtitle),
            color = AuthColors.OnBrandMuted,
            style = AppTheme.typography.bodyLarge,
            modifier = Modifier
                .widthIn(max = subtitleMaxWidth)
                .testTag(SignInTestTags.HeroSubtitle),
        )
    }
}

@Composable
private fun SignInActionSheet(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SignInTestTags.Sheet),
        shape = RoundedCornerShape(SheetCorner),
        colors = CardDefaults.cardColors(
            containerColor = AuthColors.Sheet,
            contentColor = AuthColors.Ink,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = SheetElevation),
    ) {
        Column(modifier = Modifier.padding(SheetPadding)) {
            SheetHeader()
            Spacer(Modifier.height(AppTheme.spacing.default))

            if (state.emailPasswordEnabled) {
                EmailPasswordForm(state = state, onEvent = onEvent)
                Spacer(Modifier.height(AppTheme.spacing.small))
                // Only errors that belong to no single field land here; the rest are rendered
                // as supporting text under the input that caused them.
                val formError = state.formError
                if (formError != null) {
                    Text(
                        text = formError.localized(),
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SignInTestTags.ErrorText),
                    )
                    Spacer(Modifier.height(AppTheme.spacing.small))
                }
                PrimaryFilledButton(
                    text = stringResource(
                        when (state.mode) {
                            AuthMode.SignIn -> Res.string.sign_in_submit_signin
                            AuthMode.SignUp -> Res.string.sign_in_submit_signup
                        },
                    ),
                    onClick = { onEvent(SignInEvent.OnSubmitClick) },
                    enabled = state.canSubmit,
                    modifier = Modifier.testTag(SignInTestTags.SubmitButton),
                )
                Spacer(Modifier.height(AppTheme.spacing.xSmall))
                TextButton(
                    onClick = { onEvent(SignInEvent.OnToggleModeClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SignInTestTags.ToggleModeButton),
                ) {
                    Text(
                        text = stringResource(
                            when (state.mode) {
                                AuthMode.SignIn -> Res.string.sign_in_toggle_to_signup
                                AuthMode.SignUp -> Res.string.sign_in_toggle_to_signin
                            },
                        ),
                        color = AuthColors.PrimaryDark,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.anonymousEnabled) {
                if (state.emailPasswordEnabled) {
                    OrDivider()
                }
                PasskeyOutlinedButton(
                    text = stringResource(Res.string.sign_in_anonymous),
                    onClick = { onEvent(SignInEvent.OnAnonymousLoginClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier.testTag(SignInTestTags.AnonymousButton),
                )
            }

            if (state.demoEnabled) {
                Spacer(Modifier.height(AppTheme.spacing.small))
                if (state.demoOnly) {
                    PrimaryFilledButton(
                        text = stringResource(Res.string.sign_in_demo_mode),
                        onClick = { onEvent(SignInEvent.OnLoginClick) },
                        enabled = !state.isLoading,
                        modifier = Modifier.testTag(SignInTestTags.DemoButton),
                    )
                } else {
                    TextButton(
                        onClick = { onEvent(SignInEvent.OnLoginClick) },
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SignInTestTags.DemoButton),
                    ) {
                        Text(
                            text = stringResource(Res.string.sign_in_demo_mode),
                            color = AuthColors.PrimaryDark,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppTheme.spacing.default))
            SignInTerms(onClick = { onEvent(SignInEvent.OnTermsClick) })
        }
    }
}

@Composable
private fun SheetHeader() {
    Text(
        text = stringResource(Res.string.sign_in_sheet_title),
        color = AuthColors.Ink,
        fontSize = SheetTitleSize,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SignInTestTags.SheetTitle),
    )
    Spacer(Modifier.height(AppTheme.spacing.xxSmall))
    Text(
        text = stringResource(Res.string.sign_in_sheet_subtitle),
        color = AuthColors.SheetMuted,
        style = AppTheme.typography.bodySmall,
        modifier = Modifier.testTag(SignInTestTags.SheetSubtitle),
    )
}

@Composable
private fun EmailPasswordForm(
    state: SignInState,
    onEvent: (SignInEvent) -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AuthColors.PrimaryDark,
        unfocusedBorderColor = AuthColors.Divider,
        focusedLabelColor = AuthColors.PrimaryDark,
        unfocusedLabelColor = AuthColors.SheetSubtle,
        cursorColor = AuthColors.PrimaryDark,
        focusedTextColor = AuthColors.Ink,
        unfocusedTextColor = AuthColors.Ink,
    )
    SurferTextField(
        value = state.email,
        onValueChange = { onEvent(SignInEvent.OnEmailChanged(it)) },
        label = stringResource(Res.string.sign_in_email_label),
        errorText = state.emailError?.localized(),
        enabled = !state.isLoading,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        colors = fieldColors,
        fieldTestTag = SignInTestTags.EmailField,
    )
    // No spacer: each field already reserves a message line under itself, which is the gap.
    SurferPasswordField(
        value = state.password,
        onValueChange = { onEvent(SignInEvent.OnPasswordChanged(it)) },
        label = stringResource(Res.string.sign_in_password_label),
        errorText = state.passwordError?.localized(),
        enabled = !state.isLoading,
        colors = fieldColors,
        fieldTestTag = SignInTestTags.PasswordField,
    )
}

@Composable
private fun PrimaryFilledButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(PrimaryButtonHeight),
        shape = RoundedCornerShape(PrimaryButtonHeight / 2),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthColors.PrimaryDark,
            contentColor = AuthColors.OnBrand,
        ),
    ) {
        Text(
            text = text,
            fontSize = PrimaryLabelSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PasskeyOutlinedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(PrimaryButtonHeight),
        shape = RoundedCornerShape(PrimaryButtonHeight / 2),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AuthColors.PrimaryDark,
        ),
    ) {
        Icon(
            imageVector = SurferIcons.Fingerprint,
            contentDescription = SurferSemantics.Decorative,
            modifier = Modifier.size(PasskeyIconSize),
        )
        Spacer(Modifier.width(AppTheme.spacing.small))
        Text(
            text = text,
            fontSize = PrimaryLabelSize,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthColors.Divider),
        )
        Text(
            text = stringResource(Res.string.sign_in_or).uppercase(),
            color = AuthColors.SheetSubtle,
            fontSize = OrLabelSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = AppTheme.spacing.small),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(AuthColors.Divider),
        )
    }
}

@Composable
private fun SignInTerms(onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.sign_in_terms),
            style = AppTheme.typography.bodySmall,
            color = AuthColors.PrimaryDark,
            textAlign = TextAlign.Center,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .testTag(SignInTestTags.Terms),
        )
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

@Preview
@Composable
private fun SignInScreenFieldErrorPreview() {
    AppTheme {
        SignInContent(
            state = SignInState(
                email = "surfer@example",
                password = "123",
                mode = AuthMode.SignUp,
                error = SignInError.PasswordTooShort,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun SignInScreenDemoOnlyPreview() {
    AppTheme {
        SignInContent(
            state = SignInState(
                emailPasswordEnabled = false,
                anonymousEnabled = false,
                demoEnabled = true,
            ),
            onEvent = {},
        )
    }
}
