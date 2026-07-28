package com.georgeci.moneysurfer.navigation

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.uikit.components.SurferDetailPlaceholder
import moneysurfer.navigation.generated.resources.Res
import moneysurfer.navigation.generated.resources.nav_detail_placeholder
import org.jetbrains.compose.resources.stringResource

/** Placeholder shown in the detail pane of [SurferPaneSceneStrategy] when nothing is selected. */
@Composable
fun NavDetailPlaceholder() {
    SurferDetailPlaceholder(text = stringResource(Res.string.nav_detail_placeholder))
}
