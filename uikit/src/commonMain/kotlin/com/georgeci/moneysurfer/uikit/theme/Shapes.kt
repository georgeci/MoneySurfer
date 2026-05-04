package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // 12.dp matches the design tokens for input fields (OutlinedTextField pulls its default shape
    // from `extraSmall`); aligning the slot keeps every input rounded the same without per-call
    // overrides.
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
