package com.georgeci.moneysurfer.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dropbox.differ.SimpleImageComparator
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size

/*
 * Shared Roborazzi harness. It lives in `gradle/screenshot-harness/` rather than in a module
 * because every module that captures screenshots needs the same render environment, and captures
 * are only comparable across modules if the SDK level, qualifiers and tolerances below are
 * literally the same code. `gradle/screenshot-tests.gradle.kts` adds this directory to the
 * `androidHostTest` source set — and to detekt's — of every module that applies it.
 */

/**
 * Robolectric SDK the reference screenshots are rendered against. Pinned rather than
 * derived from `targetSdk`: bumping the platform re-renders every component (font
 * metrics, ripple, shadow elevation all move), so it must be a deliberate, reviewed
 * change followed by `./gradlew recordScreenshots -Proborazzi.record=true`.
 */
const val ScreenshotSdk: Int = 34

/**
 * Device qualifiers for the reference renders. Locks density, font scale and screen
 * width so the captures are reproducible on any host — see [ScreenshotSdk].
 */
const val ScreenshotQualifiers: String = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"

/** Width every component gallery is laid out at; height wraps the content. */
private const val GalleryWidthDp = 411

/**
 * Viewport a full screen is laid out in — the `w411dp-h891dp` of [ScreenshotQualifiers]. A screen
 * sizes itself with `fillMaxSize()`, so unlike a component gallery it needs a real height to lay
 * out against; with a wrapping height the bottom-pinned sheets collapse into the content.
 */
private const val ScreenWidthDp = 411
private const val ScreenHeightDp = 891

/**
 * Per-pixel colour tolerance, as a normalised RGB distance.
 *
 * Alpha compositing rounds differently on the macOS and Linux Skia builds: capturing
 * the same component on both hosts yields images where up to ~13% of pixels differ by
 * exactly 1–2 of 255 per channel (measured on `surfer_category_components_dark`, whose
 * low-alpha tint washes are the worst case — peak distance 0.0136). Without a tolerance
 * those count as changes and the suite is red on CI while green locally.
 *
 * 0.02 sits comfortably above that noise floor and far below a real regression: a
 * changed colour token or a moved border shifts pixels by an order of magnitude more.
 */
private const val MaxPixelDistance = 0.02f

/**
 * Share of pixels that may exceed [MaxPixelDistance] before the capture counts as a
 * regression. 0.1% of the frame absorbs nothing meaningful — a one-pixel border still
 * trips it.
 */
private const val ChangeThreshold = 0.001f

private val screenshotOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        changeThreshold = ChangeThreshold,
        imageComparator = SimpleImageComparator(maxDistance = MaxPixelDistance),
    ),
)

/**
 * Renders [content] on the themed app surface and writes/verifies
 * `<module>/screenshots/<name>_light.png` and `<name>_dark.png`.
 *
 * Both themes are captured from a single test so a component can never drift in one
 * theme while the other stays green.
 */
fun captureLightAndDark(name: String, content: @Composable () -> Unit) {
    captureBothThemes(name, widthDp = GalleryWidthDp, heightDp = 0) { darkTheme ->
        SurferComponentPreview(darkTheme = darkTheme) {
            content()
        }
    }
}

/**
 * Whole-screen counterpart of [captureLightAndDark]: lays [content] out in a full device viewport
 * instead of wrapping its height, and drops the [SurferComponentPreview] card surface, since a
 * screen paints its own background edge to edge.
 *
 * That difference is the point — the committed PNG is a frame a reviewer recognises as the screen,
 * not a swatch of the components it happens to be built from.
 */
fun captureFullScreen(name: String, content: @Composable () -> Unit) {
    captureBothThemes(name, widthDp = ScreenWidthDp, heightDp = ScreenHeightDp) { darkTheme ->
        AppTheme(darkTheme = darkTheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}

@OptIn(ExperimentalRoborazziApi::class)
private fun captureBothThemes(
    name: String,
    widthDp: Int,
    heightDp: Int,
    content: @Composable (darkTheme: Boolean) -> Unit,
) {
    listOf("light" to false, "dark" to true).forEach { (suffix, darkTheme) ->
        captureRoboImage(
            filePath = "screenshots/${name}_$suffix.png",
            roborazziOptions = screenshotOptions,
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = widthDp, heightDp = heightDp)
            },
        ) {
            content(darkTheme)
        }
    }
}
