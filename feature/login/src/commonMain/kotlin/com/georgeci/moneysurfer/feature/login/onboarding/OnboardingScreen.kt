package com.georgeci.moneysurfer.feature.login.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.uikit.components.SurferAuthBackground
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.ConfigureSystemBars
import com.georgeci.moneysurfer.uikit.tokens.AuthColors
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.login.generated.resources.Res
import moneysurfer.feature.login.generated.resources.onboarding_skip
import moneysurfer.feature.login.generated.resources.onboarding_start_card_body
import moneysurfer.feature.login.generated.resources.onboarding_start_card_title
import moneysurfer.feature.login.generated.resources.onboarding_start_cash_body
import moneysurfer.feature.login.generated.resources.onboarding_start_cash_title
import moneysurfer.feature.login.generated.resources.onboarding_start_cta
import moneysurfer.feature.login.generated.resources.onboarding_start_recommended
import moneysurfer.feature.login.generated.resources.onboarding_start_savings_body
import moneysurfer.feature.login.generated.resources.onboarding_start_savings_title
import moneysurfer.feature.login.generated.resources.onboarding_start_subtitle
import moneysurfer.feature.login.generated.resources.onboarding_start_title
import moneysurfer.feature.login.generated.resources.onboarding_step_format
import moneysurfer.feature.login.generated.resources.onboarding_value_cta
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_offline_body
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_offline_title
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_together_body
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_together_title
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_track_body
import moneysurfer.feature.login.generated.resources.onboarding_value_feature_track_title
import moneysurfer.feature.login.generated.resources.onboarding_value_note_offline
import moneysurfer.feature.login.generated.resources.onboarding_value_note_online
import moneysurfer.feature.login.generated.resources.onboarding_value_subtitle
import moneysurfer.feature.login.generated.resources.onboarding_value_title
import moneysurfer.feature.login.generated.resources.sign_in_brand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the onboarding screens — see docs/testing/testing-strategy.md. */
object OnboardingTestTags {
    const val Root = "onboarding:root"
    const val Continue = "onboarding:continue"
    const val Skip = "onboarding:skip"

    fun accountKind(kind: OnboardingAccountKind): String = "onboarding:kind:${kind.name}"
}

@Composable
fun OnboardingScreen(
    onNavigateToSignIn: () -> Unit,
    onNavigateToFirstAccount: (AccountType) -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            OnboardingEffect.NavigateToSignIn -> onNavigateToSignIn()
            is OnboardingEffect.NavigateToFirstAccount -> onNavigateToFirstAccount(effect.accountType)
        }
    }

    OnboardingContent(state = state, onEvent = viewModel::onEvent)
}

/**
 * Stateless body of the onboarding flow. Internal rather than private so the screenshot captures
 * can mount it with an injected [OnboardingState] instead of standing up a Koin-backed
 * [OnboardingViewModel].
 */
@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    // Same brand backdrop as sign-in, drawn edge to edge so it tints the status bar and the
    // navigation bar / gesture ribbon; only the content is inset. Both bars sit on a light
    // surface here — the top of the gradient and the white CTA sheet — so both keep dark icons.
    ConfigureSystemBars(darkStatusBarBackground = false, darkNavigationBarBackground = false)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .surferTestTagAsId()
            .testTag(OnboardingTestTags.Root),
    ) {
        SurferAuthBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .surferSafeInsets(),
            ) {
                OnboardingHeader(state = state, onEvent = onEvent)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding),
                ) {
                    when (state.step) {
                        OnboardingStep.Value -> ValueStep(isOffline = state.isOffline)
                        OnboardingStep.FirstAccount -> FirstAccountStep(
                            selected = state.selectedAccountKind,
                            onSelect = { onEvent(OnboardingEvent.OnAccountKindSelected(it)) },
                        )
                    }
                    Spacer(Modifier.height(ScreenPadding))
                }
            }

            OnboardingCta(state = state, onEvent = onEvent)
        }
    }
}

/** Step counter + skip + segmented progress, mirroring `ObHeader` in the design. */
@Composable
private fun OnboardingHeader(
    state: OnboardingState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    if (!state.showProgress) return
    val muted = AuthColors.OnBrandSubtle
    Column(
        modifier = Modifier.padding(start = ScreenPadding, end = ScreenPadding, top = HeaderTopPadding),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_step_format, state.stepNumber, state.totalSteps),
                fontSize = LabelSize,
                fontWeight = FontWeight.SemiBold,
                color = muted,
            )
            if (state.showSkip) {
                TextButton(
                    onClick = { onEvent(OnboardingEvent.OnSkipClick) },
                    enabled = !state.inFlight,
                    modifier = Modifier
                        .surferTestTagAsId()
                        .testTag(OnboardingTestTags.Skip),
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_skip),
                        fontSize = LabelSize,
                        fontWeight = FontWeight.SemiBold,
                        color = muted,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ProgressGap)) {
            repeat(state.totalSteps) { index ->
                val done = index < state.stepNumber
                val color = if (done) AuthColors.OnBrand else AuthColors.ProgressRest
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ProgressHeight)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

@Composable
private fun ValueStep(isOffline: Boolean) {
    Spacer(Modifier.height(ValueTopGap))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(BrandTileSize)
                .clip(RoundedCornerShape(BrandTileCorner))
                .background(AuthColors.BrandTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Wallet,
                contentDescription = SurferSemantics.Decorative,
                tint = AuthColors.OnBrand,
                modifier = Modifier.size(BrandIconSize),
            )
        }
        Spacer(Modifier.size(GapSmall))
        Text(
            text = stringResource(Res.string.sign_in_brand),
            fontSize = BrandLabelSize,
            fontWeight = FontWeight.Bold,
            color = AuthColors.OnBrand,
        )
    }

    Spacer(Modifier.height(ValueTitleGap))

    Text(
        text = stringResource(Res.string.onboarding_value_title),
        fontSize = HeroTitleSize,
        lineHeight = HeroTitleLineHeight,
        fontWeight = FontWeight.ExtraBold,
        color = AuthColors.OnBrand,
    )
    Spacer(Modifier.height(GapSmall))
    Text(
        text = stringResource(Res.string.onboarding_value_subtitle),
        fontSize = BodyLargeSize,
        lineHeight = BodyLargeLineHeight,
        color = AuthColors.OnBrandMuted,
        modifier = Modifier.widthIn(max = SubtitleMaxWidth),
    )

    Spacer(Modifier.height(FeaturesGap))

    Column(verticalArrangement = Arrangement.spacedBy(GapDefault)) {
        ValueFeature(
            icon = SurferIcons.Receipt,
            title = stringResource(Res.string.onboarding_value_feature_track_title),
            body = stringResource(Res.string.onboarding_value_feature_track_body),
        )
        // The second promise is the one that actually differs per build: the offline app never
        // needs a network, the online one is about sharing a workspace.
        if (isOffline) {
            ValueFeature(
                icon = SurferIcons.Savings,
                title = stringResource(Res.string.onboarding_value_feature_offline_title),
                body = stringResource(Res.string.onboarding_value_feature_offline_body),
            )
        } else {
            ValueFeature(
                icon = SurferIcons.People,
                title = stringResource(Res.string.onboarding_value_feature_together_title),
                body = stringResource(Res.string.onboarding_value_feature_together_body),
            )
        }
    }
}

@Composable
private fun ValueFeature(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(FeatureTileSize)
                .clip(RoundedCornerShape(BrandTileCorner))
                .background(AuthColors.BrandTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = SurferSemantics.Decorative,
                tint = AuthColors.OnBrand,
                modifier = Modifier.size(BrandIconSize),
            )
        }
        Spacer(Modifier.size(GapSmall))
        Column {
            Text(
                text = title,
                fontSize = TitleSmallSize,
                fontWeight = FontWeight.Bold,
                color = AuthColors.OnBrand,
            )
            Text(
                text = body,
                fontSize = BodySmallSize,
                lineHeight = BodySmallLineHeight,
                color = AuthColors.OnBrandSubtle,
            )
        }
    }
}

@Composable
private fun FirstAccountStep(
    selected: OnboardingAccountKind,
    onSelect: (OnboardingAccountKind) -> Unit,
) {
    Spacer(Modifier.height(GapDefault))
    Text(
        text = stringResource(Res.string.onboarding_start_title),
        fontSize = HeadlineSize,
        fontWeight = FontWeight.Bold,
        color = AuthColors.OnBrand,
    )
    Spacer(Modifier.height(GapXSmall))
    Text(
        text = stringResource(Res.string.onboarding_start_subtitle),
        fontSize = BodyMediumSize,
        lineHeight = BodyLargeLineHeight,
        color = AuthColors.OnBrandMuted,
    )
    Spacer(Modifier.height(GapLarge))

    Column(verticalArrangement = Arrangement.spacedBy(GapSmall)) {
        OnboardingAccountKind.entries.forEach { kind ->
            AccountKindCard(
                kind = kind,
                selected = kind == selected,
                onClick = { onSelect(kind) },
            )
        }
    }
}

@Composable
private fun AccountKindCard(
    kind: OnboardingAccountKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) AuthColors.PrimaryDark else AuthColors.Divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .background(if (selected) AuthColors.GreenSoft else AuthColors.Sheet)
            .border(CardBorder, borderColor, RoundedCornerShape(CardCorner))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(CardPadding)
            .surferTestTagAsId()
            .testTag(OnboardingTestTags.accountKind(kind)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(KindTileSize)
                .clip(RoundedCornerShape(KindTileCorner))
                .background(if (selected) AuthColors.PrimaryDark else AuthColors.GreenSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = SurferSemantics.Decorative,
                tint = if (selected) AuthColors.OnBrand else AuthColors.PrimaryDark,
                modifier = Modifier.size(KindIconSize),
            )
        }
        Spacer(Modifier.size(GapSmall))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(kind.titleRes()),
                    fontSize = TitleMediumSize,
                    fontWeight = FontWeight.Bold,
                    color = AuthColors.Ink,
                )
                if (kind.recommended) {
                    Spacer(Modifier.size(GapXSmall))
                    RecommendedBadge()
                }
            }
            Text(
                text = stringResource(kind.bodyRes()),
                fontSize = BodySmallSize,
                lineHeight = BodySmallLineHeight,
                color = AuthColors.SheetMuted,
            )
        }
        Spacer(Modifier.size(GapXSmall))
        Box(
            modifier = Modifier
                .size(RadioSize)
                .clip(CircleShape)
                .background(if (selected) AuthColors.PrimaryDark else Color.Transparent)
                .border(CardBorder, borderColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = SurferIcons.Check,
                    contentDescription = SurferSemantics.Decorative,
                    tint = AuthColors.OnBrand,
                    modifier = Modifier.size(RadioIconSize),
                )
            }
        }
    }
}

@Composable
private fun RecommendedBadge() {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(AuthColors.Sheet)
            .border(BadgeBorder, AuthColors.Mint.copy(alpha = BadgeBorderAlpha), CircleShape)
            .padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
    ) {
        Text(
            text = stringResource(Res.string.onboarding_start_recommended),
            fontSize = BadgeLabelSize,
            fontWeight = FontWeight.Bold,
            color = AuthColors.Mint,
        )
    }
}

/** Bottom CTA. It always sits on the white sheet the design pulls over the green backdrop. */
@Composable
private fun OnboardingCta(
    state: OnboardingState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AuthColors.Sheet,
        shape = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner),
    ) {
        Column(
            // The sheet spans the full width so the green never shows beside it; it therefore has
            // to clear the side and bottom insets itself.
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(
                    start = ScreenPadding,
                    end = ScreenPadding,
                    top = CtaTopPadding,
                    bottom = CtaBottomPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = { onEvent(OnboardingEvent.OnContinueClick) },
                enabled = !state.inFlight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CtaHeight)
                    .surferTestTagAsId()
                    .testTag(OnboardingTestTags.Continue),
                shape = RoundedCornerShape(CtaHeight / 2),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthColors.PrimaryDark,
                    contentColor = AuthColors.OnBrand,
                ),
            ) {
                Text(
                    text = stringResource(
                        when (state.step) {
                            OnboardingStep.Value -> Res.string.onboarding_value_cta
                            OnboardingStep.FirstAccount -> Res.string.onboarding_start_cta
                        },
                    ),
                    fontSize = TitleMediumSize,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (state.step == OnboardingStep.Value) {
                Spacer(Modifier.height(GapXSmall))
                Text(
                    text = stringResource(
                        if (state.isOffline) {
                            Res.string.onboarding_value_note_offline
                        } else {
                            Res.string.onboarding_value_note_online
                        },
                    ),
                    fontSize = BodySmallSize,
                    lineHeight = BodySmallLineHeight,
                    color = AuthColors.SheetMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun OnboardingAccountKind.icon(): ImageVector = when (this) {
    OnboardingAccountKind.Cash -> SurferIcons.Cash
    OnboardingAccountKind.Card -> SurferIcons.CreditCard
    OnboardingAccountKind.Savings -> SurferIcons.Savings
}

private fun OnboardingAccountKind.titleRes(): StringResource = when (this) {
    OnboardingAccountKind.Cash -> Res.string.onboarding_start_cash_title
    OnboardingAccountKind.Card -> Res.string.onboarding_start_card_title
    OnboardingAccountKind.Savings -> Res.string.onboarding_start_savings_title
}

private fun OnboardingAccountKind.bodyRes(): StringResource = when (this) {
    OnboardingAccountKind.Cash -> Res.string.onboarding_start_cash_body
    OnboardingAccountKind.Card -> Res.string.onboarding_start_card_body
    OnboardingAccountKind.Savings -> Res.string.onboarding_start_savings_body
}

private val ScreenPadding: Dp = 22.dp
private val HeaderTopPadding: Dp = 16.dp
private val HeaderGap: Dp = 12.dp
private val ProgressGap: Dp = 6.dp
private val ProgressHeight: Dp = 4.dp
private val ValueTopGap: Dp = 28.dp
private val ValueTitleGap: Dp = 26.dp
private val FeaturesGap: Dp = 22.dp
private val GapXSmall: Dp = 8.dp
private val GapSmall: Dp = 12.dp
private val GapDefault: Dp = 16.dp
private val GapLarge: Dp = 22.dp
private val BrandTileSize: Dp = 36.dp
private val BrandTileCorner: Dp = 12.dp
private val BrandIconSize: Dp = 20.dp
private val FeatureTileSize: Dp = 40.dp
private val SubtitleMaxWidth: Dp = 320.dp
private val CardCorner: Dp = 18.dp
private val CardBorder: Dp = 2.dp
private val CardPadding: Dp = 16.dp
private val KindTileSize: Dp = 46.dp
private val KindTileCorner: Dp = 14.dp
private val KindIconSize: Dp = 22.dp
private val RadioSize: Dp = 22.dp
private val RadioIconSize: Dp = 14.dp
private val BadgeBorder: Dp = 1.dp
private val BadgePaddingHorizontal: Dp = 8.dp
private val BadgePaddingVertical: Dp = 2.dp
private val SheetCorner: Dp = 28.dp
private val CtaHeight: Dp = 54.dp
private val CtaTopPadding: Dp = 16.dp
private val CtaBottomPadding: Dp = 20.dp

private const val BadgeBorderAlpha = 0.33f

private val HeroTitleSize = 34.sp
private val HeroTitleLineHeight = 38.sp
private val HeadlineSize = 24.sp
private val BrandLabelSize = 16.sp
private val TitleMediumSize = 16.sp
private val TitleSmallSize = 14.sp
private val BodyLargeSize = 16.sp
private val BodyLargeLineHeight = 24.sp
private val BodyMediumSize = 14.sp
private val BodySmallSize = 12.sp
private val BodySmallLineHeight = 16.sp
private val LabelSize = 14.sp
private val BadgeLabelSize = 11.sp

@Preview
@Composable
private fun OnboardingValueStepPreview() {
    OnboardingContent(state = OnboardingState(isOffline = true), onEvent = {})
}

@Preview
@Composable
private fun OnboardingFirstAccountStepPreview() {
    OnboardingContent(
        state = OnboardingState(isOffline = true, step = OnboardingStep.FirstAccount),
        onEvent = {},
    )
}
