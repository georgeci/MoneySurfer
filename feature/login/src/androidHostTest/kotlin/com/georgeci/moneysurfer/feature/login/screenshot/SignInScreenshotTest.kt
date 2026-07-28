package com.georgeci.moneysurfer.feature.login.screenshot

import com.georgeci.moneysurfer.feature.login.AuthMode
import com.georgeci.moneysurfer.feature.login.SignInContent
import com.georgeci.moneysurfer.feature.login.SignInError
import com.georgeci.moneysurfer.feature.login.SignInErrorPresentation
import com.georgeci.moneysurfer.feature.login.SignInState
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of sign-in / registration.
 *
 * `isLoading = true` is deliberately absent: it draws `SurferFullScreenLoader`, whose spinner
 * animates forever and would capture a different frame every run — see
 * docs/testing/screenshot-tests.md.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SignInScreenshotTest {

    @Test
    fun signIn() = captureFullScreen("sign_in") {
        SignInContent(state = SignInState(), onEvent = {})
    }

    @Test
    fun signUp() = captureFullScreen("sign_in_sign_up") {
        SignInContent(
            state = SignInState(
                email = "surfer@example.com",
                password = "correct-horse",
                mode = AuthMode.SignUp,
            ),
            onEvent = {},
        )
    }

    /** Per-field validation, anchored under the input that caused it. */
    @Test
    fun signUpFieldError() = captureFullScreen("sign_in_field_error") {
        SignInContent(
            state = SignInState(
                email = "surfer@example",
                password = "123",
                mode = AuthMode.SignUp,
                error = SignInError.PasswordTooShort,
            ),
            onEvent = {},
        )
    }

    /** Errors belonging to no single field render centred under the form instead. */
    @Test
    fun signInFormError() = captureFullScreen("sign_in_form_error") {
        SignInContent(
            state = SignInState(
                email = "surfer@example.com",
                password = "wrong-password",
                error = SignInError.InvalidCredentials,
            ),
            onEvent = {},
        )
    }

    @Test
    fun signInErrorDialog() = captureFullScreen("sign_in_error_dialog") {
        SignInContent(
            state = SignInState(
                email = "surfer@example.com",
                error = SignInError.PermissionDenied,
                errorPresentation = SignInErrorPresentation.Dialog,
            ),
            onEvent = {},
        )
    }

    /** The offline build's shape: no credentials, no passkey — one demo CTA. */
    @Test
    fun signInDemoOnly() = captureFullScreen("sign_in_demo_only") {
        SignInContent(
            state = SignInState(
                emailPasswordEnabled = false,
                anonymousEnabled = false,
                demoEnabled = true,
            ),
            onEvent = {},
        )
    }
}
