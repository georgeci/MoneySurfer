package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_close
import org.jetbrains.compose.resources.stringResource

/**
 * Inner scaffold for a navigation-driven bottom sheet entry: a title, an optional subtitle, a
 * body slot, and an optional action slot pinned at the bottom. The surrounding
 * [androidx.compose.material3.ModalBottomSheet] (handle, container, scrim) is the caller's
 * responsibility — typically provided by the navigation scene strategy.
 *
 * [onClose] adds the circular dismiss button on the title row. Sheets reached through navigation
 * do not need it — the scrim and the back gesture already dismiss them — but a picker opened over
 * a form does, which is why it is opt-in rather than always present.
 */
@Composable
fun SurferBottomSheetContent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    onClose: (() -> Unit)? = null,
    button: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    CloseButton(onClose)
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        if (button != null) {
            Spacer(Modifier.height(16.dp))
            Column(modifier = Modifier.padding(contentPadding)) {
                button()
            }
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SurferIcons.Close,
            // The only content of the button, so it has to carry its name.
            contentDescription = stringResource(Res.string.uikit_close),
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
