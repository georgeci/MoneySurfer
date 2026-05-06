package com.georgeci.moneysurfer.uikit.modifier

import androidx.compose.ui.Modifier

/**
 * Mirror Compose `Modifier.testTag(...)` values onto the underlying platform's
 * accessibility resource-id so external test runners (Maestro on Android) can
 * target nodes via `id:`. No-op on platforms without that concept.
 *
 * Apply once near the root of a screen — the flag propagates to descendants.
 */
expect fun Modifier.surferTestTagAsId(): Modifier
