package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
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
 */
@OptIn(ExperimentalTestApi::class)
class SignInScreenStateTest : StringSpec({

    "submit button stays enabled on empty input so submitting can explain what is missing" {
        runComposeUiTest {
            setContent {
                SignInContent(state = SignInState(), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.Root).assertIsDisplayed()
            onNodeWithTag(SignInTestTags.SubmitButton).assertIsEnabled()
        }
    }

    "submit button is enabled once the state can be submitted" {
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
            setContent {
                SignInContent(state = SignInState(password = "secret1"), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.PasswordField).assertTextEquals("Password", "•••••••")
            onNodeWithTag(SignInTestTags.PasswordReveal).performClick()
            onNodeWithTag(SignInTestTags.PasswordField).assertTextEquals("Password", "secret1")
        }
    }

    "no error state renders no error text" {
        runComposeUiTest {
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
        runComposeUiTest {
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

        runComposeUiTest {
            setContent {
                SignInContent(state = SignInState(), onEvent = {})
            }

            onNodeWithTag(SignInTestTags.Root).assertIsDisplayed()
        }
    }
})

/** Where the submit button sits, so two states can be compared for layout shift. */
@OptIn(ExperimentalTestApi::class)
private fun submitButtonTop(state: SignInState): Float {
    var top = Float.NaN
    runComposeUiTest {
        setContent { SignInContent(state = state, onEvent = {}) }
        top = onNodeWithTag(SignInTestTags.SubmitButton).fetchSemanticsNode().positionInRoot.y
    }
    return top
}
