package com.georgeci.moneysurfer.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRadio
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.tokens.AccentSeeds
import com.georgeci.moneysurfer.uikit.tokens.AppColors
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_appearance_accent_footnote
import moneysurfer.feature.settings.generated.resources.settings_appearance_color_source
import moneysurfer.feature.settings.generated.resources.settings_appearance_dynamic_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_dynamic_title
import moneysurfer.feature.settings.generated.resources.settings_appearance_live_preview_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_live_preview_title
import moneysurfer.feature.settings.generated.resources.settings_appearance_swatch_brand
import moneysurfer.feature.settings.generated.resources.settings_appearance_swatch_mint
import moneysurfer.feature.settings.generated.resources.settings_appearance_swatch_plum
import moneysurfer.feature.settings.generated.resources.settings_appearance_swatch_rose
import moneysurfer.feature.settings.generated.resources.settings_appearance_swatch_sky
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_dark
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_dark_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_light
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_light_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_section
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_system
import moneysurfer.feature.settings.generated.resources.settings_appearance_theme_system_supporting
import moneysurfer.feature.settings.generated.resources.settings_appearance_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed interface SwatchSpec {
    val labelRes: StringResource
    val color: Color

    data class Brand(override val color: Color) : SwatchSpec {
        override val labelRes: StringResource = Res.string.settings_appearance_swatch_brand
    }

    data class Preset(
        val seed: AccentSeed,
        override val labelRes: StringResource,
        override val color: Color,
    ) : SwatchSpec
}

private val FixedSwatches: List<SwatchSpec> = listOf(
    SwatchSpec.Brand(AppColors.Primary),
    SwatchSpec.Preset(AccentSeed.Plum, Res.string.settings_appearance_swatch_plum, AccentSeeds.Plum),
    SwatchSpec.Preset(AccentSeed.Sky, Res.string.settings_appearance_swatch_sky, AccentSeeds.Sky),
    SwatchSpec.Preset(AccentSeed.Mint, Res.string.settings_appearance_swatch_mint, AccentSeeds.Mint),
    SwatchSpec.Preset(AccentSeed.Rose, Res.string.settings_appearance_swatch_rose, AccentSeeds.Rose),
)

/**
 * Stable selectors for the Appearance screen — see docs/testing/testing-strategy.md.
 *
 * The option rows are named after the enum entry they set, not the localized label, so a
 * renamed row title never breaks a flow: `appearance:theme:system|light|dark` and
 * `appearance:cardStyle:filled|outlined|card`.
 */
object AppearanceTestTags {
    const val Root = "appearance:root"

    /** Row that selects a [ThemeMode]. */
    fun theme(mode: ThemeMode): String = "appearance:theme:${mode.name.lowercase()}"

    /** Row that selects a [ContainerStyle] — the "Card style" section. */
    fun cardStyle(style: ContainerStyle): String = "appearance:cardStyle:${style.name.lowercase()}"
}

@Composable
fun AppearanceScreen(
    onNavigateBack: () -> Unit,
    viewModel: AppearanceViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    AppearanceContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onDynamicSelect = { viewModel.onEvent(AppearanceEvent.OnDynamicSelect) },
        onBrandSelect = { viewModel.onEvent(AppearanceEvent.OnBrandSelect) },
        onPresetSelect = { seed -> viewModel.onEvent(AppearanceEvent.OnPresetSelect(seed)) },
        onThemeModeSelect = { mode -> viewModel.onEvent(AppearanceEvent.OnThemeModeSelect(mode)) },
        onContainerStyleSelect = { style ->
            viewModel.onEvent(AppearanceEvent.OnContainerStyleSelect(style))
        },
    )
}

@Composable
private fun AppearanceContent(
    state: AppearanceState,
    onNavigateBack: () -> Unit,
    onDynamicSelect: () -> Unit,
    onBrandSelect: () -> Unit,
    onPresetSelect: (AccentSeed) -> Unit,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onContainerStyleSelect: (ContainerStyle) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(AppearanceTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_appearance_title),
                onBack = onNavigateBack,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                LiveSchemePreview()
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_appearance_theme_section)) {
                SurferSettingsRow(
                    icon = SurferIcons.Sparkle,
                    title = stringResource(Res.string.settings_appearance_theme_system),
                    supportingText = stringResource(Res.string.settings_appearance_theme_system_supporting),
                    onClick = { onThemeModeSelect(ThemeMode.System) },
                    trailing = { SurferSettingsRadio(selected = state.themeMode == ThemeMode.System) },
                    modifier = Modifier.testTag(AppearanceTestTags.theme(ThemeMode.System)),
                )
                SurferSettingsRow(
                    icon = SurferIcons.LightMode,
                    title = stringResource(Res.string.settings_appearance_theme_light),
                    supportingText = stringResource(Res.string.settings_appearance_theme_light_supporting),
                    onClick = { onThemeModeSelect(ThemeMode.Light) },
                    trailing = { SurferSettingsRadio(selected = state.themeMode == ThemeMode.Light) },
                    modifier = Modifier.testTag(AppearanceTestTags.theme(ThemeMode.Light)),
                )
                SurferSettingsRow(
                    icon = SurferIcons.DarkMode,
                    title = stringResource(Res.string.settings_appearance_theme_dark),
                    supportingText = stringResource(Res.string.settings_appearance_theme_dark_supporting),
                    onClick = { onThemeModeSelect(ThemeMode.Dark) },
                    trailing = { SurferSettingsRadio(selected = state.themeMode == ThemeMode.Dark) },
                    modifier = Modifier.testTag(AppearanceTestTags.theme(ThemeMode.Dark)),
                )
            }

            SurferSettingsGroup(
                title = stringResource(Res.string.settings_appearance_color_source),
                footnote = stringResource(Res.string.settings_appearance_accent_footnote),
            ) {
                if (state.isDynamicColorAvailable) {
                    SurferSettingsRow(
                        icon = SurferIcons.Sparkle,
                        title = stringResource(Res.string.settings_appearance_dynamic_title),
                        supportingText = stringResource(Res.string.settings_appearance_dynamic_supporting),
                        onClick = onDynamicSelect,
                        trailing = { SurferSettingsRadio(selected = state.isDynamicColorEnabled) },
                    )
                }
                SeedPickerCard(
                    swatches = FixedSwatches,
                    isBrandSelected = state.isBrandSelected,
                    selectedSeed = state.selectedSeed,
                    onPickBrand = onBrandSelect,
                    onPickPreset = onPresetSelect,
                )
            }

            SurferSettingsGroup(title = "Card style") {
                ContainerStyle.entries.forEach { style ->
                    SurferSettingsRow(
                        icon = containerStyleIcon(style),
                        title = style.displayLabel(),
                        supportingText = style.displaySupporting(),
                        onClick = { onContainerStyleSelect(style) },
                        trailing = { SurferSettingsRadio(selected = state.containerStyle == style) },
                        modifier = Modifier.testTag(AppearanceTestTags.cardStyle(style)),
                    )
                }
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }
}

private fun ContainerStyle.displayLabel(): String = when (this) {
    ContainerStyle.Filled -> "Filled"
    ContainerStyle.Outlined -> "Outlined"
    ContainerStyle.Card -> "Card"
}

private fun ContainerStyle.displaySupporting(): String = when (this) {
    ContainerStyle.Filled -> "Solid surfaces, no border"
    ContainerStyle.Outlined -> "Bordered surfaces, no fill"
    ContainerStyle.Card -> "Elevated surfaces with shadow"
}

private fun containerStyleIcon(style: ContainerStyle) = when (style) {
    ContainerStyle.Filled -> SurferIcons.Palette
    ContainerStyle.Outlined -> SurferIcons.Code
    ContainerStyle.Card -> SurferIcons.Receipt
}

@Composable
private fun LiveSchemePreview() {
    val colors = AppTheme.materialColors
    SurferCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.Palette,
                    contentDescription = SurferSemantics.Decorative,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_appearance_live_preview_title),
                    style = AppTheme.typography.titleSmall,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(Res.string.settings_appearance_live_preview_supporting),
                    style = AppTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(colors.primary, colors.secondary, colors.tertiary).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(c),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedPickerCard(
    swatches: List<SwatchSpec>,
    isBrandSelected: Boolean,
    selectedSeed: AccentSeed?,
    onPickBrand: () -> Unit,
    onPickPreset: (AccentSeed) -> Unit,
) {
    val colors = AppTheme.materialColors
    SurferCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            swatches.forEach { swatch ->
                val selected = when (swatch) {
                    is SwatchSpec.Brand -> isBrandSelected
                    is SwatchSpec.Preset -> swatch.seed == selectedSeed
                }
                SwatchCell(
                    label = stringResource(swatch.labelRes),
                    color = swatch.color,
                    selected = selected,
                    labelColor = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    onClick = {
                        when (swatch) {
                            is SwatchSpec.Brand -> onPickBrand()
                            is SwatchSpec.Preset -> onPickPreset(swatch.seed)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SwatchCell(
    label: String,
    color: Color,
    selected: Boolean,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(color)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SurferIcons.Check,
                        contentDescription = SurferSemantics.Decorative,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = labelColor,
        )
    }
}

@Preview()
@Composable
private fun AppearanceScreenPreview() {
    AppTheme {
        AppearanceContent(
            state = AppearanceState(isDynamicColorAvailable = true),
            onNavigateBack = {},
            onDynamicSelect = {},
            onBrandSelect = {},
            onPresetSelect = {},
            onThemeModeSelect = {},
            onContainerStyleSelect = {},
        )
    }
}

@Preview
@Composable
private fun AppearanceScreenDynamicOnPreview() {
    AppTheme {
        AppearanceContent(
            state = AppearanceState(
                isDynamicColorAvailable = true,
                paletteSource = PaletteSource.Dynamic,
            ),
            onNavigateBack = {},
            onDynamicSelect = {},
            onBrandSelect = {},
            onPresetSelect = {},
            onThemeModeSelect = {},
            onContainerStyleSelect = {},
        )
    }
}
