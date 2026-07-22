package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.runtime.Composable
import com.dropbox.differ.SimpleImageComparator
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size

/**
 * Robolectric SDK the reference screenshots are rendered against. Pinned rather than
 * derived from `targetSdk`: bumping the platform re-renders every component (font
 * metrics, ripple, shadow elevation all move), so it must be a deliberate, reviewed
 * change followed by `./gradlew :uikit:recordScreenshots -Proborazzi.record=true`.
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
 * `uikit/screenshots/<name>_light.png` and `<name>_dark.png`.
 *
 * Both themes are captured from a single test so a component can never drift in one
 * theme while the other stays green.
 */
@OptIn(ExperimentalRoborazziApi::class)
fun captureLightAndDark(name: String, content: @Composable () -> Unit) {
    listOf("light" to false, "dark" to true).forEach { (suffix, darkTheme) ->
        captureRoboImage(
            filePath = "screenshots/${name}_$suffix.png",
            roborazziOptions = screenshotOptions,
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = GalleryWidthDp, heightDp = 0)
            },
        ) {
            SurferComponentPreview(darkTheme = darkTheme) {
                content()
            }
        }
    }
}
