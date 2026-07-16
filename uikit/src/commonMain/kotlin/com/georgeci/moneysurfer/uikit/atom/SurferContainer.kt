package com.georgeci.moneysurfer.uikit.atom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

// ─── Filled ──────────────────────────────────────────────────────────

@Immutable
internal data class SurferFilledContainerTokens(
    val background: Color,
    val contentColor: Color,
    val shape: Shape,
    val contentPadding: PaddingValues,
)

internal object SurferFilledContainerDefaults {
    @Composable
    @ReadOnlyComposable
    fun default(): SurferFilledContainerTokens = SurferFilledContainerTokens(
        background = AppTheme.materialColors.surfaceContainer,
        contentColor = AppTheme.materialColors.onSurface,
        shape = AppTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    )

    @Composable
    @ReadOnlyComposable
    fun selected(): SurferFilledContainerTokens = SurferFilledContainerTokens(
        background = AppTheme.materialColors.primary,
        contentColor = Color.White,
        shape = AppTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
internal fun SurferFilledContainer(
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    tokens: SurferFilledContainerTokens = SurferFilledContainerDefaults.default(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(tokens.shape)
            .then(interactionModifier)
            .background(tokens.background)
            .padding(tokens.contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.contentColor) {
            content()
        }
    }
}

// ─── Outlined ────────────────────────────────────────────────────────

@Immutable
internal data class SurferOutlinedContainerTokens(
    val background: Color,
    val stroke: Color,
    val strokeWidth: Dp,
    val contentColor: Color,
    val shape: Shape,
    val contentPadding: PaddingValues,
)

internal object SurferOutlinedContainerDefaults {
    @Composable
    @ReadOnlyComposable
    fun default(): SurferOutlinedContainerTokens = SurferOutlinedContainerTokens(
        background = Color.Transparent,
        stroke = AppTheme.materialColors.outlineVariant,
        strokeWidth = 1.dp,
        contentColor = AppTheme.materialColors.onSurface,
        shape = AppTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    )

    @Composable
    @ReadOnlyComposable
    fun selected(): SurferOutlinedContainerTokens {
        val cs = AppTheme.materialColors
        return SurferOutlinedContainerTokens(
            background = cs.primary.copy(alpha = 0.08f).compositeOver(cs.surface),
            stroke = cs.primary,
            strokeWidth = 1.dp,
            contentColor = cs.onSurface,
            shape = AppTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
        )
    }
}

@Composable
internal fun SurferOutlinedContainer(
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    tokens: SurferOutlinedContainerTokens = SurferOutlinedContainerDefaults.default(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(tokens.shape)
            .then(interactionModifier)
            .background(tokens.background)
            .border(BorderStroke(tokens.strokeWidth, tokens.stroke), shape = tokens.shape)
            .padding(tokens.contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.contentColor) {
            content()
        }
    }
}

// ─── Card ────────────────────────────────────────────────────────────

@Immutable
internal data class SurferContainerTokens(
    val background: Color,
    val stroke: Color,
    val strokeWidth: Dp,
    val shadowElevation: Dp,
    val contentColor: Color,
    val shape: Shape,
    val contentPadding: PaddingValues,
)

internal object SurferContainerDefaults {
    @Composable
    @ReadOnlyComposable
    fun default(): SurferContainerTokens = SurferContainerTokens(
        background = AppTheme.materialColors.surfaceContainerLowest,
        stroke = Color.Transparent,
        strokeWidth = 0.dp,
        shadowElevation = 2.dp,
        contentColor = AppTheme.materialColors.onSurface,
        shape = AppTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    )

    @Composable
    @ReadOnlyComposable
    fun selected(): SurferContainerTokens = SurferContainerTokens(
        background = AppTheme.materialColors.surfaceContainerLowest,
        stroke = AppTheme.materialColors.primary,
        strokeWidth = 1.dp,
        shadowElevation = 8.dp,
        contentColor = AppTheme.materialColors.onSurface,
        shape = AppTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    )
}

@Composable
internal fun SurferContainer(
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    tokens: SurferContainerTokens = SurferContainerDefaults.default(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation = tokens.shadowElevation, shape = tokens.shape)
            .clip(tokens.shape)
            .then(interactionModifier)
            .background(tokens.background)
            .border(BorderStroke(tokens.strokeWidth, tokens.stroke), shape = tokens.shape)
            .padding(tokens.contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.contentColor) {
            content()
        }
    }
}

// ─── AddAction ───────────────────────────────────────────────────────

@Immutable
internal data class SurferAddActionContainerTokens(
    val background: Color,
    val stroke: Color,
    val strokeWidth: Dp,
    val dashLength: Dp,
    val dashGap: Dp,
    val contentColor: Color,
    val shape: Shape,
    val contentPadding: PaddingValues,
)

internal object SurferAddActionContainerDefaults {
    @Composable
    @ReadOnlyComposable
    fun default(): SurferAddActionContainerTokens {
        val cs = AppTheme.materialColors
        return SurferAddActionContainerTokens(
            background = Color.Transparent,
            stroke = cs.outline,
            strokeWidth = 1.5.dp,
            dashLength = 6.dp,
            dashGap = 4.dp,
            contentColor = cs.primary,
            shape = AppTheme.shapes.large,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
internal fun SurferAddActionContainer(
    modifier: Modifier = Modifier,
    interactionModifier: Modifier = Modifier,
    tokens: SurferAddActionContainerTokens = SurferAddActionContainerDefaults.default(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(tokens.shape)
            .then(interactionModifier)
            .background(tokens.background)
            .dashedBorder(
                color = tokens.stroke,
                strokeWidth = tokens.strokeWidth,
                dashLength = tokens.dashLength,
                dashGap = tokens.dashGap,
                shape = tokens.shape,
            )
            .padding(tokens.contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.contentColor) {
            content()
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    dashLength: Dp,
    dashGap: Dp,
    shape: Shape,
): Modifier = this.drawBehind {
    val stroke = strokeWidth.toPx()
    val inset = stroke / 2f
    val cornerRadius = when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(Size(size.width, size.height), this)
        else -> 0f
    }
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius - inset, cornerRadius - inset),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), dashGap.toPx()),
                0f,
            ),
        ),
    )
}

// ─── Previews ────────────────────────────────────────────────────────

@Preview
@Composable
private fun SurferFilledContainerPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            SurferFilledContainer(modifier = Modifier.fillMaxWidth()) {
                PreviewRowBody("Main account", "Checking · ••4218", "$4,210.55")
            }
            SurferFilledContainer(
                modifier = Modifier.fillMaxWidth(),
                tokens = SurferFilledContainerDefaults.selected(),
            ) {
                PreviewRowBody("Savings", "High-yield · 4.5%", "$12,840.00")
            }
        }
    }
}

@Preview
@Composable
private fun SurferOutlinedContainerPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            SurferOutlinedContainer(modifier = Modifier.fillMaxWidth()) {
                PreviewRowBody("Main account", "Checking · ••4218", "$4,210.55")
            }
            SurferOutlinedContainer(
                modifier = Modifier.fillMaxWidth(),
                tokens = SurferOutlinedContainerDefaults.selected(),
            ) {
                PreviewRowBody("Savings", "High-yield · 4.5%", "$12,840.00")
            }
        }
    }
}

@Preview
@Composable
private fun SurferContainerPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            SurferContainer(modifier = Modifier.fillMaxWidth()) {
                PreviewRowBody("Main account", "Checking · ••4218", "$4,210.55")
            }
            SurferContainer(
                modifier = Modifier.fillMaxWidth(),
                tokens = SurferContainerDefaults.selected(),
            ) {
                PreviewRowBody("Savings", "High-yield · 4.5%", "$12,840.00")
            }
        }
    }
}

@Preview
@Composable
private fun SurferAddActionContainerPreview() {
    SurferComponentPreview {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SurferAddActionContainer(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = SurferIcons.Add, contentDescription = null) // decorative — preview only; label text is adjacent
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Add account", style = AppTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PreviewRowBody(label: String, hint: String, amount: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = AppTheme.typography.titleSmall)
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = hint,
                style = AppTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        Text(text = amount, style = AppTheme.typography.titleSmall)
    }
}
