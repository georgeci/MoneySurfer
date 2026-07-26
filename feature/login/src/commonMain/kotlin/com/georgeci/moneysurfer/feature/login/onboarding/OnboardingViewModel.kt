package com.georgeci.moneysurfer.feature.login.onboarding

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

/**
 * First-launch welcome flow, modelled on the `Onboarding` design (screens 01 "Ценность" and an
 * adapted 03 "С чего начнём?").
 *
 * The offline build gets both steps: after the value pitch the user picks what their first
 * account looks like, and that choice pre-fills the account creation screen — whose workspace
 * this view model seeds on the way out via [FirstRunSeeder]. The online build has nothing to pick
 * yet (accounts live inside a workspace that only exists after sign-in), so it stops at the value
 * step and continues into sign-in.
 *
 * `onboardingCompleted` is persisted before navigating, so a process death right after the tap
 * doesn't replay the onboarding. Skipping completes it too, keeping the default selection.
 */
@KoinViewModel
class OnboardingViewModel(
    private val uiPreferences: UiPreferences,
    private val firstRunSeeder: FirstRunSeeder,
    hostCapabilities: HostCapabilities,
) : MviViewModel<OnboardingState, OnboardingEvent, OnboardingEffect>(
    initialState = OnboardingState(isOffline = hostCapabilities.isOffline),
) {

    private val log = Logger.withTag(TAG)

    override fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.OnContinueClick -> advance()
            OnboardingEvent.OnSkipClick -> finish()
            OnboardingEvent.OnBackClick -> updateState { copy(step = OnboardingStep.Value) }
            is OnboardingEvent.OnAccountKindSelected ->
                updateState { copy(selectedAccountKind = event.kind) }
        }
    }

    /** Value step moves on to the offline picker when there is one; otherwise it finishes. */
    private fun advance() {
        val state = currentState
        if (state.inFlight) return
        if (state.step == OnboardingStep.Value && state.isOffline) {
            updateState { copy(step = OnboardingStep.FirstAccount) }
        } else {
            finish()
        }
    }

    private fun finish() {
        val state = currentState
        if (state.inFlight) return
        launch(
            onError = { err ->
                log.w(err) { "[finish] failed — onboarding not marked complete, user can retry" }
                updateState { copy(inFlight = false) }
            },
        ) {
            updateState { copy(inFlight = true) }
            val effect = if (state.isOffline) {
                // The offline first-account screen needs a pinned workspace, so the seed runs
                // here rather than at launch — the app introduces itself before writing anything.
                firstRunSeeder.seedIfNeeded()
                OnboardingEffect.NavigateToFirstAccount(state.selectedAccountKind.accountType)
            } else {
                OnboardingEffect.NavigateToSignIn
            }
            // Flag last: if the seed above threw, the onboarding replays on the next launch
            // instead of dropping the user on a screen with no workspace behind it.
            uiPreferences.onboardingCompleted.set(true)
            log.i { "[finish] ok -> $effect" }
            postSideEffect(effect)
        }
    }

    private companion object {
        const val TAG = "OnboardingVM"
    }
}

enum class OnboardingStep { Value, FirstAccount }

/**
 * The three starting points offered on the offline picker, in design order. [recommended] drives
 * the "Рекомендуем" badge — cash is the one every user can fill in without looking anything up.
 */
enum class OnboardingAccountKind(
    val accountType: AccountType,
    val recommended: Boolean = false,
) {
    Cash(AccountType.CASH, recommended = true),
    Card(AccountType.CARD),
    Savings(AccountType.SAVINGS),
}

data class OnboardingState(
    val isOffline: Boolean = false,
    val step: OnboardingStep = OnboardingStep.Value,
    val selectedAccountKind: OnboardingAccountKind = OnboardingAccountKind.Cash,
    val inFlight: Boolean = false,
) {
    /** Offline walks value → first account; online stops at the value pitch. */
    val totalSteps: Int get() = if (isOffline) OFFLINE_STEPS else ONLINE_STEPS

    val stepNumber: Int get() = when (step) {
        OnboardingStep.Value -> 1
        OnboardingStep.FirstAccount -> 2
    }

    /** A single-step flow has no progress to report. */
    val showProgress: Boolean get() = totalSteps > ONLINE_STEPS

    /** The last step's CTA commits, so "skip" would be the same button twice. */
    val showSkip: Boolean get() = step == OnboardingStep.Value && showProgress

    private companion object {
        const val ONLINE_STEPS = 1
        const val OFFLINE_STEPS = 2
    }
}

sealed interface OnboardingEvent {
    data object OnContinueClick : OnboardingEvent
    data object OnSkipClick : OnboardingEvent
    data object OnBackClick : OnboardingEvent
    data class OnAccountKindSelected(val kind: OnboardingAccountKind) : OnboardingEvent
}

sealed interface OnboardingEffect {
    data object NavigateToSignIn : OnboardingEffect

    /** Offline: continue into account creation, pre-filled with the picked [accountType]. */
    data class NavigateToFirstAccount(val accountType: AccountType) : OnboardingEffect
}
