package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferDetailPlaceholder
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.SurferErrorState
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Placeholder / empty / error states.
 *
 * `SurferFullScreenLoader` and `SurferSkeleton` are deliberately absent: both drive an
 * infinite animation, so the captured frame depends on when the render happened and the
 * comparison would flake. Cover those with behavioural tests instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferStateScreenshotTest {

    @Test
    fun surferEmptyAndErrorStates() = captureLightAndDark("surfer_states") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SurferEmptyState(
                title = "No transactions yet",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Add your first transaction to see it here.",
                icon = SurferIcons.Receipt,
                actionLabel = "Add transaction",
                onActionClick = {},
            )
            SurferErrorState(
                title = "Couldn't load accounts",
                modifier = Modifier.fillMaxWidth(),
                subtitle = "Check your connection and try again.",
                icon = SurferIcons.Cloud,
                retryLabel = "Retry",
                onRetry = {},
            )
            SurferDetailPlaceholder(
                text = "Select an account to see its details",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
