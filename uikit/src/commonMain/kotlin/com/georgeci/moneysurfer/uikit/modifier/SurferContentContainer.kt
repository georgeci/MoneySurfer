package com.georgeci.moneysurfer.uikit.modifier

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Widest a screen's content is allowed to get before it stops growing and starts centring.
 *
 * 840 dp is the Material 3 Expanded lower bound: below it the cap never engages, so phones and
 * small tablets are untouched, and above it a settings row or a transaction line keeps a readable
 * measure instead of stretching across the window. Screens that genuinely want more room (a wide
 * chart, a two-column form) pass their own value to [surferContentContainer].
 */
val SurferContentMaxWidth: Dp = 840.dp

/**
 * Cap for the layouts that genuinely use the extra room, where [SurferContentMaxWidth] would be a
 * straitjacket rather than a readable measure — a widget grid, a two-column form.
 *
 * A single value rather than one per screen: it was picked twice independently, for the dashboard
 * grid and for the sign-in split, and two hand-tuned copies of the same number drift.
 */
val SurferWideContentMaxWidth: Dp = 1120.dp

/**
 * Caps content at [maxWidth] and centres it horizontally in whatever space the parent offers.
 *
 * Apply at a screen's content root, *before* the padding and scrolling modifiers, so the cap
 * bounds the content rather than the window:
 *
 * ```
 * Column(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .surferContentContainer()
 *         .padding(padding)
 *         .verticalScroll(rememberScrollState()),
 * )
 * ```
 *
 * The chain is a no-op whenever the parent is narrower than [maxWidth]: [fillMaxWidth] pins the
 * outer width, [wrapContentWidth] relaxes the child's minimum so it can be narrower and centres
 * whatever comes back, [widthIn] applies the cap, and the trailing [fillMaxWidth] hands the child
 * the same *fixed* width it used to get — so content that relied on filling its parent keeps
 * filling it, just within the cap.
 */
fun Modifier.surferContentContainer(maxWidth: Dp = SurferContentMaxWidth): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = maxWidth)
    .fillMaxWidth()
