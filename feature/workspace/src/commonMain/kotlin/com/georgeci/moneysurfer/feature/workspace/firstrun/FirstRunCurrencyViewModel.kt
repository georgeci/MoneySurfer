package com.georgeci.moneysurfer.feature.workspace.firstrun

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

/**
 * Drives the first-launch currency picker. The offline seed creates a workspace with a
 * locale-derived currency; this screen lets the user confirm or change it, then persists the
 * choice on the workspace + seeded account and flips `session.currencyChosen` so the picker
 * never reappears.
 *
 * The user can also "Skip" — that keeps the locale-derived seed currency, flips `currencyChosen`
 * so the picker is not forced again, and sets `onboardingSkipped` so Settings can offer a
 * "Finish setup" entry. Confirming the picker (first run or via "Finish setup") clears
 * `onboardingSkipped`.
 */
@KoinViewModel
class FirstRunCurrencyViewModel(
    private val getCurrencies: GetCurrenciesUseCase,
    private val updateWorkspaceCurrency: UpdateWorkspaceCurrencyUseCase,
    private val workspaceRepository: WorkspaceRepository,
    private val session: SessionPointers,
) : MviViewModel<FirstRunCurrencyState, FirstRunCurrencyEvent, FirstRunCurrencyEffect>(
    initialState = FirstRunCurrencyState.Loading,
) {

    private val log = Logger.withTag(TAG)

    private var workspaceId: WorkspaceId? = null

    init { load() }

    override fun onEvent(event: FirstRunCurrencyEvent) {
        when (event) {
            is FirstRunCurrencyEvent.OnQueryChanged -> updateState {
                if (this is FirstRunCurrencyState.Content) copy(query = event.query) else this
            }
            is FirstRunCurrencyEvent.OnCurrencySelected -> updateState {
                if (this is FirstRunCurrencyState.Content) {
                    copy(selectedCode = event.code, error = false)
                } else {
                    this
                }
            }
            FirstRunCurrencyEvent.OnConfirmClick -> confirm()
            FirstRunCurrencyEvent.OnSkipClick -> skip()
        }
    }

    private fun skip() {
        val state = currentState
        if (state is FirstRunCurrencyState.Content && state.inFlight) return
        launch(onError = { log.w(it) { "[skip] failed" } }) {
            session.currencyChosen.set(true)
            session.onboardingSkipped.set(true)
            log.i { "[skip] keeping seeded currency, onboarding skipped" }
            postSideEffect(FirstRunCurrencyEffect.NavigateToDashboard)
        }
    }

    private fun load() {
        launch(onError = { log.w(it) { "[load] failed" } }) {
            val currencies = getCurrencies().first().orderedForPicker()
            val wid = session.currentWorkspaceId.flow.first()
            workspaceId = wid
            val workspaceCurrency = wid?.let { workspaceRepository.getById(it)?.baseCurrency?.value }
            val selected = currencies.firstOrNull { it.code.value == workspaceCurrency }?.code?.value
                ?: currencies.firstOrNull()?.code?.value
            updateState {
                FirstRunCurrencyState.Content(
                    currencies = currencies,
                    selectedCode = selected,
                )
            }
        }
    }

    private fun confirm() {
        val state = currentState as? FirstRunCurrencyState.Content ?: return
        val code = state.selectedCode
        val wid = workspaceId
        if (code == null || wid == null || state.inFlight) return
        launch(
            onError = { err ->
                log.w(err) { "[confirm] unexpected exception" }
                updateState {
                    if (this is FirstRunCurrencyState.Content) copy(inFlight = false, error = true) else this
                }
            },
        ) {
            updateState {
                if (this is FirstRunCurrencyState.Content) copy(inFlight = true, error = false) else this
            }
            updateWorkspaceCurrency(wid, CurrencyCode(code)).fold(
                ifLeft = { err ->
                    log.w { "[confirm] update failed -> $err" }
                    updateState {
                        if (this is FirstRunCurrencyState.Content) copy(inFlight = false, error = true) else this
                    }
                },
                ifRight = {
                    session.currencyChosen.set(true)
                    session.onboardingSkipped.set(false)
                    log.i { "[confirm] ok currency=$code wid=${wid.value}" }
                    postSideEffect(FirstRunCurrencyEffect.NavigateToDashboard)
                },
            )
        }
    }

    private companion object {
        const val TAG = "FirstRunCurrencyVM"
    }
}

sealed interface FirstRunCurrencyState {
    data object Loading : FirstRunCurrencyState

    data class Content(
        val currencies: List<Currency>,
        val selectedCode: String?,
        val query: String = "",
        val inFlight: Boolean = false,
        val error: Boolean = false,
    ) : FirstRunCurrencyState {
        /** Catalogue narrowed by the current search query. */
        val visibleCurrencies: List<Currency> get() = currencies.filteredBy(query)
    }
}

sealed interface FirstRunCurrencyEvent {
    data class OnQueryChanged(val query: String) : FirstRunCurrencyEvent
    data class OnCurrencySelected(val code: String) : FirstRunCurrencyEvent
    data object OnConfirmClick : FirstRunCurrencyEvent
    data object OnSkipClick : FirstRunCurrencyEvent
}

sealed interface FirstRunCurrencyEffect {
    data object NavigateToDashboard : FirstRunCurrencyEffect
}
