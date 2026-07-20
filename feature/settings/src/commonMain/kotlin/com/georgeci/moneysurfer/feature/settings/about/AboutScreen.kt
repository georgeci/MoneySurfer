package com.georgeci.moneysurfer.feature.settings.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_about_brand
import moneysurfer.feature.settings.generated.resources.settings_about_chip_license
import moneysurfer.feature.settings.generated.resources.settings_about_chip_made_by
import moneysurfer.feature.settings.generated.resources.settings_about_contact_supporting
import moneysurfer.feature.settings.generated.resources.settings_about_contact_title
import moneysurfer.feature.settings.generated.resources.settings_about_copyright
import moneysurfer.feature.settings.generated.resources.settings_about_github_supporting
import moneysurfer.feature.settings.generated.resources.settings_about_github_title
import moneysurfer.feature.settings.generated.resources.settings_about_licenses_supporting
import moneysurfer.feature.settings.generated.resources.settings_about_licenses_title
import moneysurfer.feature.settings.generated.resources.settings_about_privacy_supporting
import moneysurfer.feature.settings.generated.resources.settings_about_privacy_title
import moneysurfer.feature.settings.generated.resources.settings_about_section_help
import moneysurfer.feature.settings.generated.resources.settings_about_section_legal
import moneysurfer.feature.settings.generated.resources.settings_about_terms_title
import moneysurfer.feature.settings.generated.resources.settings_about_title
import moneysurfer.feature.settings.generated.resources.settings_about_version_format
import moneysurfer.uikit.generated.resources.uikit_app_icon
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import moneysurfer.uikit.generated.resources.Res as UikitRes

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            AboutEffect.NavigateBack -> onNavigateBack()
            is AboutEffect.OpenUrl -> uriHandler.openUri(effect.url)
            is AboutEffect.OpenEmail -> uriHandler.openUri("mailto:${effect.address}")
            AboutEffect.NavigateToLicenses -> onNavigateToLicenses()
            AboutEffect.OpenStoreListing,
            AboutEffect.OpenRegionPicker,
            AboutEffect.NavigateToDiagnostic,
            -> Unit
        }
    }

    AboutContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun AboutContent(
    state: AboutState,
    onEvent: (AboutEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_about_title),
                onBack = { onEvent(AboutEvent.OnBackClick) },
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
            AppIdentityHero(version = state.appVersion)

            GitHubLinkRow(onClick = { onEvent(AboutEvent.OnGitHubClick) })

            Spacer(Modifier.height(20.dp))

            SurferSettingsGroup(title = stringResource(Res.string.settings_about_section_legal)) {
                SurferSettingsRow(
                    icon = SurferIcons.Receipt,
                    title = stringResource(Res.string.settings_about_terms_title),
                    onClick = { onEvent(AboutEvent.OnTermsClick) },
                    trailing = { SurferSettingsChevron() },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Shield,
                    title = stringResource(Res.string.settings_about_privacy_title),
                    supportingText = stringResource(Res.string.settings_about_privacy_supporting),
                    onClick = { onEvent(AboutEvent.OnPrivacyClick) },
                    trailing = { SurferSettingsChevron() },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Code,
                    title = stringResource(Res.string.settings_about_licenses_title),
                    supportingText = stringResource(Res.string.settings_about_licenses_supporting),
                    onClick = { onEvent(AboutEvent.OnLicensesClick) },
                    trailing = { SurferSettingsChevron() },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_about_section_help)) {
                SurferSettingsRow(
                    icon = SurferIcons.Mail,
                    title = stringResource(Res.string.settings_about_contact_title),
                    supportingText = stringResource(Res.string.settings_about_contact_supporting),
                    onClick = { onEvent(AboutEvent.OnContactClick) },
                    trailing = { SurferSettingsChevron() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.settings_about_copyright),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            )
            Spacer(Modifier.height(padding.calculateBottomPadding() + 28.dp))
        }
    }
}

@Composable
private fun AppIdentityHero(version: String) {
    val colors = AppTheme.materialColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(UikitRes.drawable.uikit_app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp)),
        )
        Text(
            text = stringResource(Res.string.settings_about_brand),
            style = AppTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(Res.string.settings_about_version_format, version),
            style = AppTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroChip(text = stringResource(Res.string.settings_about_chip_made_by))
            HeroChip(text = stringResource(Res.string.settings_about_chip_license))
        }
    }
}

@Composable
private fun GitHubLinkRow(onClick: () -> Unit) {
    val colors = AppTheme.materialColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(Res.string.settings_about_github_title),
                style = AppTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(Res.string.settings_about_github_supporting),
                style = AppTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroChip(text: String) {
    val colors = AppTheme.materialColors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = AppTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
