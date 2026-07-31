package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.georgeci.moneysurfer.feature.login.AuthMode
import com.georgeci.moneysurfer.feature.login.SignInContent
import com.georgeci.moneysurfer.feature.login.SignInError
import com.georgeci.moneysurfer.feature.login.SignInErrorPresentation
import com.georgeci.moneysurfer.feature.login.SignInEvent
import com.georgeci.moneysurfer.feature.login.SignInState
import com.georgeci.moneysurfer.feature.login.SignInTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.awt.GraphicsEnvironment

/**
 * Spike for the JVM desktop UI-testing rollout (docs/plans/jvm-desktop-testing-rollout.md).
 *
 * Answers step 1's two questions: `runComposeUiTest` runs inside a kotest [StringSpec] block, and
 * it renders headless (offscreen Skiko) with no display attached.
 *
 * Every case is pinned to a phone window by [runSignInTest]. `runComposeUiTest`'s default is
 * 1024 × 768 — expanded *and* landscape — which sign-in now lays out as two columns, so leaving
 * these on the default would quietly move the whole file onto the split layout and leave the
 * stacked one, the layout nearly every user sees, with no state coverage at all. Geometry of the
 * wide layouts is `SignInResponsiveLayoutTest`'s job.
 */
@OptIn(ExperimentalTestApi::class)
class SignInScreenStateTest : StringSpec({

    "submit button stays enabled on empty input so submitting can explain what is missing" {
        runSignInTest {
            setContent {
                SignInContent(state = SignInState(), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.Root).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.SubmitButton).assertIsEnabled()
        }
    }

    "submit button is enabled once the state can be submitted" {
        runSignInTest {
            setContent {
                SignInContent(
                    state = SignInState(email = "surfer@example.com", password = "secret1"),
                    onEvent = {},
                )
            }

            onNodeWithTag(SignInTestTags.SubmitButton).assertIsEnabled()
        }
    }

    "loading state shows the full screen loader and blocks the mode toggle" {
        runSignInTest {
            setContent {
                SignInContent(
                    state = SignInState(
                        email = "surfer@example.com",
                        password = "secret1",
                        isLoading = true,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(SignInTestTags.Loader).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.SubmitButton).assertIsNotEnabled()
            onNodeWithTag(SignInTestTags.ToggleModeButton).assertIsNotEnabled()
        }
    }

    "inline form-level error renders the shared error text" {
        runSignInTest {
            setContent {
                SignInContent(
                    state = SignInState(error = SignInError.InvalidCredentials),
                    onEvent = {},
                )
            }

            onNodeWithTag(SignInTestTags.ErrorText).assertIsDisplayed()
        }
    }

    "auth error renders a dialog instead of field or form text" {
        runSignInTest {
            val events = mutableListOf<SignInEvent>()
            setContent {
                SignInContent(
                    state = SignInState(
                        error = SignInError.InvalidCredentials,
                        errorPresentation = SignInErrorPresentation.Dialog,
                    ),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(SignInTestTags.ErrorDialog).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.ErrorText).assertDoesNotExist()
            onNodeWithTag(SignInTestTags.ErrorDialogConfirm).performClick()
            events shouldContainExactly listOf(SignInEvent.OnErrorDismiss)
        }
    }

    "email error renders under the email field, not as the shared error text" {
        runSignInTest {
            setContent {
                SignInContent(
                    state = SignInState(error = SignInError.EmailInvalid),
                    onEvent = {},
                )
            }

            onNodeWithTag(SignInTestTags.EmailError, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.ErrorText).assertDoesNotExist()
        }
    }

    "password error renders under the password field" {
        runSignInTest {
            setContent {
                SignInContent(
                    state = SignInState(mode = AuthMode.SignUp, error = SignInError.PasswordTooShort),
                    onEvent = {},
                )
            }

            onNodeWithTag(SignInTestTags.PasswordError, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.ErrorText).assertDoesNotExist()
        }
    }

    "password is masked until the reveal toggle is tapped" {
        runSignInTest {
            setContent {
                SignInContent(state = SignInState(password = "secret1"), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.PasswordField).assertTextEquals("Password", "•••••••")
            onNodeWithTag(SignInTestTags.PasswordReveal).performClick()
            onNodeWithTag(SignInTestTags.PasswordField).assertTextEquals("Password", "secret1")
        }
    }

    "no error state renders no error text" {
        runSignInTest {
            setContent {
                SignInContent(state = SignInState(), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.ErrorText).assertDoesNotExist()
            // The message line under each field is always laid out (see the layout test below),
            // so what "no error" means here is that it carries no text.
            onNodeWithTag(SignInTestTags.EmailError, useUnmergedTree = true).onChildren().assertCountEquals(0)
            onNodeWithTag(SignInTestTags.PasswordError, useUnmergedTree = true).onChildren().assertCountEquals(0)
        }
    }

    "a field error does not move the rest of the form" {
        val clean = submitButtonTop(SignInState())
        val failed = submitButtonTop(SignInState(email = "surfer@example", error = SignInError.EmailInvalid))

        // The message line under a field is reserved whether or not there is a message, so the
        // button under it stays put instead of jumping the moment validation fails.
        failed shouldBe clean
    }

    "clicking submit and toggle emits the matching events" {
        runSignInTest {
            val events = mutableListOf<SignInEvent>()
            setContent {
                SignInContent(
                    state = SignInState(
                        email = "surfer@example.com",
                        password = "secret1",
                        mode = AuthMode.SignIn,
                    ),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(SignInTestTags.SubmitButton).performClick()
            onNodeWithTag(SignInTestTags.ToggleModeButton).performClick()
            waitForIdle()

            events shouldContainExactly listOf(SignInEvent.OnSubmitClick, SignInEvent.OnToggleModeClick)
        }
    }

    "composition renders with no display attached" {
        GraphicsEnvironment.isHeadless() shouldBe true

        runSignInTest {
            setContent {
                SignInContent(state = SignInState(), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.Root).assertIsDisplayed()
        }
    }
})

/** A 411 × 891 phone — Compact, so these cases exercise the stacked layout. */
private const val PHONE_WIDTH_PX = 411f
private const val PHONE_HEIGHT_PX = 891f

@OptIn(ExperimentalTestApi::class)
private fun runSignInTest(block: suspend SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), block = block)

/** Where the submit button sits, so two states can be compared for layout shift. */
@OptIn(ExperimentalTestApi::class)
private fun submitButtonTop(state: SignInState): Float {
    var top = Float.NaN
    runSignInTest {
        setContent { SignInContent(state = state, onEvent = {}) }
        top = onNodeWithTag(SignInTestTags.SubmitButton).fetchSemanticsNode().positionInRoot.y
    }
    return top
}
