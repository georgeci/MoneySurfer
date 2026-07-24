package com.georgeci.moneysurfer.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.uikit.components.SurferErrorBoundary
import com.georgeci.moneysurfer.uikit.components.SurferErrorBoundaryTestTags
import com.georgeci.moneysurfer.uikit.components.SurferErrorStateTestTags
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.MviViewModel
import com.georgeci.moneysurfer.utils.UnhandledErrors
import io.kotest.core.spec.style.StringSpec

private const val ContentTag = "boundaryTest:content"

/** Stands in for a DAO that blows up — the failure a feature never anticipated. */
private class FailingDao {
    suspend fun load(): Nothing = error("database is gone")
}

/**
 * A feature ViewModel that does not handle its own failures: exactly the shape
 * `MviViewModel.launch` escalates to the app-level boundary.
 */
private class FailingViewModel(dao: FailingDao) : MviViewModel<String, Unit, Unit>("") {

    init {
        launch { dao.load() }
    }

    override fun onEvent(event: Unit) = Unit
}

/**
 * End-to-end check for issue #78: an exception raised below the ViewModel surfaces as the
 * global error state, and Retry hands the user back to the content.
 *
 * Mirrors the wiring in `App.kt` — the real `App()` needs a started Koin graph and the full
 * nav graph, neither of which this assertion depends on.
 */
@Composable
private fun BoundaryHarness(startFailingWork: () -> Unit) {
    var hasUnhandledError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        UnhandledErrors.errors.collect { hasUnhandledError = true }
    }

    AppTheme {
        SurferErrorBoundary(
            hasError = hasUnhandledError,
            onRetry = { hasUnhandledError = false },
        ) {
            Text("content", modifier = Modifier.testTag(ContentTag))
        }
    }

    LaunchedEffect(Unit) { startFailingWork() }
}

@OptIn(ExperimentalTestApi::class)
class GlobalErrorBoundaryTest : StringSpec({

    "an exception below the ViewModel renders the global error state" {
        runComposeUiTest {
            setContent {
                BoundaryHarness(startFailingWork = { FailingViewModel(FailingDao()) })
            }
            waitForIdle()

            onNodeWithTag(SurferErrorBoundaryTestTags.Fallback).assertIsDisplayed()
        }
    }

    "retry dismisses the error state and returns to the content" {
        runComposeUiTest {
            setContent {
                BoundaryHarness(startFailingWork = { FailingViewModel(FailingDao()) })
            }
            waitForIdle()

            onNodeWithTag(SurferErrorStateTestTags.RetryButton).performClick()
            waitForIdle()

            onNodeWithTag(SurferErrorBoundaryTestTags.Fallback).assertDoesNotExist()
            onNodeWithTag(ContentTag).assertIsDisplayed()
        }
    }

    "content stays composed underneath the error state" {
        runComposeUiTest {
            setContent {
                BoundaryHarness(startFailingWork = { FailingViewModel(FailingDao()) })
            }
            waitForIdle()

            onNodeWithTag(SurferErrorBoundaryTestTags.Fallback).assertIsDisplayed()
            onNodeWithTag(ContentTag).assertExists()
        }
    }
})
