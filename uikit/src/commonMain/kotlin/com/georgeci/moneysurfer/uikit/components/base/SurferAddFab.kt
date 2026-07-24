package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics

/**
 * The "add a thing" extended FAB every list screen ends with.
 *
 * The accessible name is set here rather than left to each caller: half the screens wrapped the
 * FAB in `semantics { contentDescription = label }` and half relied on the label text alone, so
 * the same control announced itself differently depending on which list you were looking at.
 */
@Composable
fun SurferAddFab(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = SurferIcons.Add,
) {
    ExtendedFloatingActionButton(
        text = { Text(label) },
        icon = {
            Icon(imageVector = icon, contentDescription = SurferSemantics.Decorative)
        },
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
    )
}

@Preview
@Composable
private fun SurferAddFabPreview() {
    SurferComponentPreview {
        SurferAddFab(label = "Add account", onClick = {})
    }
}
