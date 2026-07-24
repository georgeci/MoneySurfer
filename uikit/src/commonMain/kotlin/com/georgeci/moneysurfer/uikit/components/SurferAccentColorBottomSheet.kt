package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferAccentColorOption(
    val key: String,
    val name: String,
    val color: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferAccentColorBottomSheet(
    title: String,
    description: String?,
    options: List<SurferAccentColorOption>,
    selectedKey: String?,
    onSelect: (SurferAccentColorOption) -> Unit,
    onDismiss: () -> Unit,
    dynamicTitle: String? = null,
    dynamicSubtitle: String? = null,
    dynamicEnabled: Boolean = false,
    onToggleDynamic: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.materialColors.onSurface,
                )
                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SwatchGrid(
                options = options,
                selectedKey = selectedKey,
                dynamicEnabled = dynamicEnabled,
                onSelect = onSelect,
            )
            if (onToggleDynamic != null && dynamicTitle != null) {
                Spacer(Modifier.height(8.dp))
                DynamicColorTile(
                    title = dynamicTitle,
                    subtitle = dynamicSubtitle.orEmpty(),
                    enabled = dynamicEnabled,
                    onToggle = onToggleDynamic,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwatchGrid(
    options: List<SurferAccentColorOption>,
    selectedKey: String?,
    dynamicEnabled: Boolean,
    onSelect: (SurferAccentColorOption) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { opt ->
            val isSelected = opt.key == selectedKey && !dynamicEnabled
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SwatchTile(
                    color = opt.color,
                    selected = isSelected,
                    onClick = { onSelect(opt) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = opt.name,
                    style = AppTheme.typography.labelMedium,
                    color = if (isSelected) AppTheme.materialColors.onSurface else AppTheme.materialColors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SwatchTile(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val ring = AppTheme.materialColors.primary
    val surface = AppTheme.materialColors.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .then(
                if (selected) {
                    Modifier
                        .border(3.dp, surface, RoundedCornerShape(16.dp))
                        .border(5.dp, ring, RoundedCornerShape(19.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
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
}

@Composable
private fun DynamicColorTile(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val border = if (enabled) AppTheme.materialColors.primary else AppTheme.materialColors.outlineVariant
    val bg = if (enabled) AppTheme.materialColors.primaryContainer else Color.Transparent
    val fg = if (enabled) AppTheme.materialColors.onPrimaryContainer else AppTheme.materialColors.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (enabled) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.materialColors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Palette,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.titleSmall,
                color = fg,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySmall,
                    color = if (enabled) fg else AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (enabled) AppTheme.materialColors.primary else Color.Transparent)
                .border(
                    2.dp,
                    if (enabled) AppTheme.materialColors.primary else AppTheme.materialColors.outline,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (enabled) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AppTheme.materialColors.onPrimary),
                )
            }
        }
    }
}
