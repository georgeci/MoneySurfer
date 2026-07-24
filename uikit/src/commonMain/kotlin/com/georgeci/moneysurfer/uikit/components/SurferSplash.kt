package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_app_icon
import moneysurfer.uikit.generated.resources.uikit_brand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen launch placeholder shown while the app decides where to navigate. Deliberately
 * static — no data, no view model — so it can render on the very first frame, before Koin
 * graph resolution finishes anywhere else.
 */
@Composable
fun SurferSplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.materialColors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
        ) {
            Image(
                painter = painterResource(Res.drawable.uikit_app_icon),
                contentDescription = SurferSemantics.Decorative,
                modifier = Modifier
                    .size(ICON_SIZE)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(ICON_CORNER))
                    .clip(RoundedCornerShape(ICON_CORNER)),
            )
            Text(
                text = stringResource(Res.string.uikit_brand),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.materialColors.onBackground,
            )
            CircularProgressIndicator(
                modifier = Modifier.size(PROGRESS_SIZE),
                color = AppTheme.materialColors.primary,
                trackColor = AppTheme.materialColors.surfaceVariant,
                strokeWidth = 3.dp,
            )
        }
    }
}

private val ICON_SIZE = 96.dp
private val ICON_CORNER = 24.dp
private val PROGRESS_SIZE = 28.dp

@Preview
@Composable
private fun SurferSplashPreview() {
    SurferComponentPreview {
        SurferSplash()
    }
}
