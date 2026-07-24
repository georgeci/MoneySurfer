package com.georgeci.moneysurfer.feature.login.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.login.generated.resources.Res
import moneysurfer.feature.login.generated.resources.legal_privacy_body
import moneysurfer.feature.login.generated.resources.legal_privacy_heading
import moneysurfer.feature.login.generated.resources.legal_terms_body
import moneysurfer.feature.login.generated.resources.legal_terms_heading
import moneysurfer.feature.login.generated.resources.legal_title
import moneysurfer.feature.login.generated.resources.legal_updated
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

const val LegalScreenTestTag: String = "legal:root"

@Composable
fun LegalScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(LegalScreenTestTag),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.legal_title),
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
                .padding(
                    top = padding.calculateTopPadding(),
                    start = AppTheme.spacing.large,
                    end = AppTheme.spacing.large,
                )
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(AppTheme.spacing.small))
            LegalSection(
                icon = SurferIcons.Receipt,
                heading = Res.string.legal_terms_heading,
                body = Res.string.legal_terms_body,
            )
            Spacer(Modifier.height(AppTheme.spacing.large))
            LegalSection(
                icon = SurferIcons.Shield,
                heading = Res.string.legal_privacy_heading,
                body = Res.string.legal_privacy_body,
            )
            Spacer(Modifier.height(AppTheme.spacing.large))
            Text(
                text = stringResource(Res.string.legal_updated),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.height(padding.calculateBottomPadding() + AppTheme.spacing.large))
        }
    }
}

@Composable
private fun LegalSection(icon: ImageVector, heading: StringResource, body: StringResource) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(heading),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
    Spacer(Modifier.height(AppTheme.spacing.xxSmall))
    Text(
        text = stringResource(body),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.materialColors.onSurfaceVariant,
    )
}
