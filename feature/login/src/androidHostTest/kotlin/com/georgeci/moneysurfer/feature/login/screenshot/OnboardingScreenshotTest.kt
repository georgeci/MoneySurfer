package com.georgeci.moneysurfer.feature.login.screenshot

import com.georgeci.moneysurfer.feature.login.onboarding.OnboardingAccountKind
import com.georgeci.moneysurfer.feature.login.onboarding.OnboardingContent
import com.georgeci.moneysurfer.feature.login.onboarding.OnboardingState
import com.georgeci.moneysurfer.feature.login.onboarding.OnboardingStep
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of the first-launch onboarding flow.
 *
 * The states are picked to cover what actually differs between builds rather than every
 * permutation: the online build stops at the value pitch (one step, so no progress rail and no
 * skip), the offline build walks two steps and ends on the account picker.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class OnboardingScreenshotTest {

    @Test
    fun onboardingValueOnline() = captureFullScreen("onboarding_value_online") {
        OnboardingContent(state = OnboardingState(isOffline = false), onEvent = {})
    }

    @Test
    fun onboardingValueOffline() = captureFullScreen("onboarding_value_offline") {
        OnboardingContent(state = OnboardingState(isOffline = true), onEvent = {})
    }

    @Test
    fun onboardingFirstAccount() = captureFullScreen("onboarding_first_account") {
        OnboardingContent(
            state = OnboardingState(
                isOffline = true,
                step = OnboardingStep.FirstAccount,
            ),
            onEvent = {},
        )
    }

    /** The non-default pick, so the selected-card treatment is visible on something. */
    @Test
    fun onboardingFirstAccountSavingsSelected() =
        captureFullScreen("onboarding_first_account_savings") {
            OnboardingContent(
                state = OnboardingState(
                    isOffline = true,
                    step = OnboardingStep.FirstAccount,
                    selectedAccountKind = OnboardingAccountKind.Savings,
                ),
                onEvent = {},
            )
        }
}
