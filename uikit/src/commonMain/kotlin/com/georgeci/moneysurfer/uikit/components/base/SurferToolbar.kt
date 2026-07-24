package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_back
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferToolbar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    SurferToolbar(
        modifier = modifier,
        navigationIcon = {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        imageVector = SurferIcons.Back,
                        contentDescription = stringResource(Res.string.uikit_back),
                    )
                }
                navigationIcon != null -> navigationIcon()
            }
        },
        colors = colors,
        actions = actions,
        title = { Text(title) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferToolbar(
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            SurferSyncStatusBadge()
            actions()
        },
        colors = colors,
    )
}

/**
 * Icon action for the trailing edge of [SurferToolbar] / [SurferDashboardToolbar]. Wraps
 * [IconButton] so every toolbar action gets the same 48dp tap target, tint, and content-
 * description handling without repeating boilerplate at every call site.
 */
@Composable
fun SurferToolbarAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.materialColors.onSurface,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Pill-shaped tonal button with a leading icon and a label, sized for the trailing edge of
 * [SurferToolbar]. Use for primary commit-style toolbar actions ("Save", "Update") that need
 * more weight than a [SurferToolbarAction] icon.
 */
@Composable
fun SurferToolbarButtonAction(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.padding(end = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = SurferSemantics.Decorative,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = AppTheme.typography.labelLarge)
    }
}

/**
 * Dashboard-flavoured top app bar: a colored letter bubble on the left, a two-line title
 * (small label + larger headline), and trailing actions. Use for the home/dashboard screen
 * where the toolbar needs to identify the active workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferDashboardToolbar(
    letter: String,
    primaryText: String,
    secondaryText: String,
    modifier: Modifier = Modifier,
    letterBackgroundColor: Color = AppTheme.materialColors.primaryContainer,
    letterContentColor: Color = AppTheme.materialColors.onPrimaryContainer,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    SurferToolbar(
        modifier = modifier,
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(letterBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = AppTheme.typography.titleSmall,
                    color = letterContentColor,
                )
            }
        },
        colors = colors,
        actions = actions,
        title = {
            Column {
                Text(
                    text = primaryText,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
                Text(
                    text = secondaryText,
                    style = AppTheme.typography.titleSmall,
                    color = AppTheme.materialColors.onSurface,
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarPreview() {
    SurferComponentPreview {
        SurferToolbar(title = "Screen Title")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithBackPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Details with back",
//            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithActionsPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Dashboard",
            actions = {
                SurferToolbarAction(
                    icon = SurferIcons.Settings,
                    contentDescription = "Settings",
                    onClick = {},
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarFullPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Edit Account",
            onBack = {},
            actions = {
                SurferToolbarAction(
                    icon = SurferIcons.Delete,
                    contentDescription = "Delete",
                    onClick = {},
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithButtonActionPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "New transaction",
            onBack = {},
            actions = {
                SurferToolbarButtonAction(
                    icon = SurferIcons.Check,
                    text = "Save",
                    onClick = {},
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferToolbarWithButtonActionDisabledPreview() {
    SurferComponentPreview {
        SurferToolbar(
            title = "Edit account",
            onBack = {},
            actions = {
                SurferToolbarButtonAction(
                    icon = SurferIcons.Check,
                    text = "Update",
                    onClick = {},
                    enabled = false,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SurferDashboardToolbarPreview() {
    SurferComponentPreview {
        SurferDashboardToolbar(
            letter = "F",
            primaryText = "Family",
            secondaryText = "Good evening, Kasia",
            actions = {
                SurferToolbarAction(
                    icon = SurferIcons.Notifications,
                    contentDescription = "Notifications",
                    onClick = {},
                )
                SurferToolbarAction(
                    icon = SurferIcons.Settings,
                    contentDescription = "Settings",
                    onClick = {},
                )
            },
        )
    }
}
