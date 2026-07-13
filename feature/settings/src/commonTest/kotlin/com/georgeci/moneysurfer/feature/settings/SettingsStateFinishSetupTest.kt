package com.georgeci.moneysurfer.feature.settings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The "Finish setup" entry is surfaced only while onboarding was skipped — confirming the
 * currency picker clears `onboardingSkipped`, which hides the entry again.
 */
class SettingsStateFinishSetupTest : StringSpec({

    "showFinishSetup is true while onboarding was skipped" {
        SettingsState(onboardingSkipped = true).showFinishSetup shouldBe true
    }

    "showFinishSetup is false once onboarding is finished" {
        SettingsState(onboardingSkipped = false).showFinishSetup shouldBe false
    }

    "showFinishSetup is independent of the offline gating" {
        SettingsState(onboardingSkipped = true, isOffline = true).showFinishSetup shouldBe true
    }
})
