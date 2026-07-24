package com.georgeci.moneysurfer.feature.login.onboarding

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.AccountType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The onboarding flow from the `Onboarding` design: a value pitch for both builds, plus an
 * offline-only "what does your first account look like?" step whose answer pre-fills account
 * creation. The online build has no workspace yet, so it stops after the pitch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "online build is a single step that continues into sign-in and marks onboarding completed" {
        runTest {
            val prefs = FakeUiPreferences()
            val viewModel = OnboardingViewModel(prefs, RecordingSeeder(), OfflineBuildFlags(isOffline = false))

            viewModel.currentState.totalSteps shouldBe 1
            viewModel.currentState.showProgress shouldBe false
            viewModel.currentState.showSkip shouldBe false

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(OnboardingEvent.OnContinueClick)
                awaitItem() shouldBe OnboardingEffect.NavigateToSignIn
            }
            prefs.onboardingCompleted.flow.first() shouldBe true
        }
    }

    "offline build advances to the first-account step before finishing" {
        runTest {
            val prefs = FakeUiPreferences()
            val viewModel = OnboardingViewModel(prefs, RecordingSeeder(), OfflineBuildFlags(isOffline = true))

            viewModel.currentState.totalSteps shouldBe 2
            viewModel.currentState.showSkip shouldBe true

            viewModel.onEvent(OnboardingEvent.OnContinueClick)

            viewModel.currentState.step shouldBe OnboardingStep.FirstAccount
            viewModel.currentState.stepNumber shouldBe 2
            // The commit step owns the CTA, so there is nothing left to skip past.
            viewModel.currentState.showSkip shouldBe false
            // Nothing is persisted until the flow actually finishes.
            prefs.onboardingCompleted.flow.first() shouldBe false
        }
    }

    "the picked account kind is handed to account creation" {
        runTest {
            val prefs = FakeUiPreferences()
            val viewModel = OnboardingViewModel(prefs, RecordingSeeder(), OfflineBuildFlags(isOffline = true))

            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            viewModel.onEvent(OnboardingEvent.OnAccountKindSelected(OnboardingAccountKind.Card))

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(OnboardingEvent.OnContinueClick)
                awaitItem() shouldBe OnboardingEffect.NavigateToFirstAccount(AccountType.CARD)
            }
            prefs.onboardingCompleted.flow.first() shouldBe true
        }
    }

    "skipping keeps the recommended cash default and still completes the onboarding" {
        runTest {
            val prefs = FakeUiPreferences()
            val viewModel = OnboardingViewModel(prefs, RecordingSeeder(), OfflineBuildFlags(isOffline = true))

            viewModel.sideEffects.effectFlow.test {
                viewModel.onEvent(OnboardingEvent.OnSkipClick)
                awaitItem() shouldBe OnboardingEffect.NavigateToFirstAccount(AccountType.CASH)
            }
            prefs.onboardingCompleted.flow.first() shouldBe true
        }
    }

    "back returns from the first-account step to the value step" {
        runTest {
            val viewModel = OnboardingViewModel(
                FakeUiPreferences(),
                RecordingSeeder(),
                OfflineBuildFlags(isOffline = true),
            )

            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            viewModel.onEvent(OnboardingEvent.OnBackClick)

            viewModel.currentState.step shouldBe OnboardingStep.Value
        }
    }

    "a failed flag write leaves the screen retryable instead of navigating on" {
        runTest {
            val viewModel = OnboardingViewModel(
                FailingUiPreferences(),
                RecordingSeeder(),
                OfflineBuildFlags(isOffline = false),
            )

            // `inFlight` is cleared and no navigation effect is posted — the user stays on the
            // screen and can retry instead of landing in a half-set-up app.
            viewModel.onEvent(OnboardingEvent.OnContinueClick)

            viewModel.currentState.inFlight shouldBe false
        }
    }

    "offline finish seeds the workspace before handing off to account creation" {
        runTest {
            val seeder = RecordingSeeder()
            val viewModel = OnboardingViewModel(FakeUiPreferences(), seeder, OfflineBuildFlags(isOffline = true))

            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            viewModel.onEvent(OnboardingEvent.OnContinueClick)

            seeder.calls shouldBe 1
        }
    }

    "a failed seed leaves the onboarding unfinished so the next launch replays it" {
        runTest {
            val prefs = FakeUiPreferences()
            val viewModel = OnboardingViewModel(prefs, FailingSeeder, OfflineBuildFlags(isOffline = true))

            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            viewModel.onEvent(OnboardingEvent.OnContinueClick)

            prefs.onboardingCompleted.flow.first() shouldBe false
            viewModel.currentState.inFlight shouldBe false
        }
    }

    "online finish does not touch the first-run seed" {
        runTest {
            val seeder = RecordingSeeder()
            val viewModel = OnboardingViewModel(FakeUiPreferences(), seeder, OfflineBuildFlags(isOffline = false))

            viewModel.onEvent(OnboardingEvent.OnContinueClick)

            seeder.calls shouldBe 0
        }
    }

    "a double tap on the CTA only finishes the onboarding once" {
        runTest {
            val seeder = GatedSeeder()
            val viewModel = OnboardingViewModel(
                FakeUiPreferences(),
                seeder,
                OfflineBuildFlags(isOffline = true),
            )

            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            // Suspends inside the seed, so the screen is in flight when the second tap lands.
            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            viewModel.onEvent(OnboardingEvent.OnContinueClick)
            seeder.release()

            seeder.calls shouldBe 1
        }
    }

    "cash is the recommended starting point" {
        OnboardingAccountKind.entries.filter { it.recommended } shouldBe listOf(OnboardingAccountKind.Cash)
    }
})

private class RecordingSeeder : FirstRunSeeder {
    var calls = 0
        private set

    override suspend fun seedIfNeeded() {
        calls++
    }
}

/** Seeds only when [release] is called, so a test can hold the flow in its in-flight state. */
private class GatedSeeder : FirstRunSeeder {
    private val gate = CompletableDeferred<Unit>()

    var calls = 0
        private set

    fun release() = gate.complete(Unit)

    override suspend fun seedIfNeeded() {
        calls++
        gate.await()
    }
}

private object FailingSeeder : FirstRunSeeder {
    override suspend fun seedIfNeeded() = error("simulated local seed failure")
}

private class FailingUiPreferences : FakeUiPreferences(onboardingCompleted = false) {
    override val onboardingCompleted: Pref<Boolean> = object : Pref<Boolean> {
        override val flow = flowOf(false)
        override suspend fun set(value: Boolean) = error("simulated DataStore write failure")
    }
}
