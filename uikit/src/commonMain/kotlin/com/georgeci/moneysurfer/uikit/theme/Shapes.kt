package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // `OutlinedTextField` pulls its default shape from `extraSmall`, so this slot is what every
    // input that is not a `SurferTextField` gets. It carries the same 16.dp as `large` on purpose:
    // fields sit in lists and forms next to `SurferCard`s, and a field rounded differently from
    // the card above it reads as a control borrowed from another screen.
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    /** The container corner: `SurferCard`, and every form field, follow this one. */
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
