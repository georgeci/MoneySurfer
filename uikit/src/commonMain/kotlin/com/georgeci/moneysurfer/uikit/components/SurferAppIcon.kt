package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_app_icon
import org.jetbrains.compose.resources.painterResource

/**
 * The launcher icon, rounded to [size] / 4 the way the platforms mask it.
 *
 * Anywhere the app introduces itself — splash, drawer header, About, the pre-auth brand line — it
 * shows this and not a stand-in glyph: a wallet outline next to the wordmark reads as a generic
 * finance app rather than as MoneySurfer.
 */
@Composable
fun SurferAppIcon(
    modifier: Modifier = Modifier,
    size: Dp = DefaultSize,
) {
    Image(
        painter = painterResource(Res.drawable.uikit_app_icon),
        contentDescription = SurferSemantics.Decorative,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / CornerRatio)),
    )
}

private val DefaultSize: Dp = 40.dp

/** Matches the squircle the launcher masks the icon with closely enough at every size we draw. */
private const val CornerRatio = 4

@Preview
@Composable
private fun SurferAppIconPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferAppIcon(size = 24.dp)
            SurferAppIcon()
            SurferAppIcon(size = 64.dp)
        }
    }
}
