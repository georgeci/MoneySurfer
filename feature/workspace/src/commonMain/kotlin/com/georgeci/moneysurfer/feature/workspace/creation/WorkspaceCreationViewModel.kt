package com.georgeci.moneysurfer.feature.workspace.creation

import arrow.optics.optics
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceError
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class WorkspaceCreationViewModel(
    private val workspaceRepository: WorkspaceRepository,
    private val createWorkspace: CreateWorkspaceUseCase,
    private val getCurrencies: GetCurrenciesUseCase,
) : MviViewModel<WorkspaceCreationState, WorkspaceCreationEvent, WorkspaceCreationEffect>(
    initialState = WorkspaceCreationState.Loading,
) {

    private val log = Logger.withTag(TAG)

    init { loadCurrencies() }

    fun init(workspaceId: WorkspaceId?) {
        if (workspaceId == null) return
        val current = currentState
        if (current is WorkspaceCreationState.Content && current.workspaceId == workspaceId) return
        launch {
            val workspace = workspaceRepository.getById(workspaceId) ?: return@launch
            updateState {
                when (this) {
                    is WorkspaceCreationState.Loading -> WorkspaceCreationState.Content(
                        workspaceId = workspace.id,
                        name = workspace.name,
                        description = workspace.description,
                        currency = workspace.baseCurrency.value,
                        currencies = emptyList(),
                        members = PREVIEW_MEMBERS,
                        isEditing = true,
                    )
                    is WorkspaceCreationState.Content -> copy(
                        workspaceId = workspace.id,
                        name = workspace.name,
                        description = workspace.description,
                        currency = workspace.baseCurrency.value,
                        isEditing = true,
                    )
                }
            }
        }
    }

    override fun onEvent(event: WorkspaceCreationEvent) {
        when (event) {
            is WorkspaceCreationEvent.OnNameChanged ->
                updateState {
                    WorkspaceCreationState.content.modify(this) { it.copy(name = event.name, error = null) }
                }
            is WorkspaceCreationEvent.OnDescriptionChanged ->
                updateState {
                    WorkspaceCreationState.content.modify(this) {
                        it.copy(description = event.description, error = null)
                    }
                }
            is WorkspaceCreationEvent.OnCurrencyChanged ->
                updateState {
                    WorkspaceCreationState.content.modify(this) {
                        it.copy(currency = event.currency, error = null)
                    }
                }
            WorkspaceCreationEvent.OnInviteMemberClick -> Unit
            WorkspaceCreationEvent.OnSaveClick -> saveWorkspace()
            WorkspaceCreationEvent.OnBackClick -> postSideEffect(WorkspaceCreationEffect.NavigateBack)
        }
    }

    private fun loadCurrencies() {
        launch {
            val currencies = getCurrencies().first()
            val defaultCurrency = currencies.firstOrNull()?.code?.value.orEmpty()
            updateState {
                when (this) {
                    is WorkspaceCreationState.Loading -> WorkspaceCreationState.Content(
                        workspaceId = null,
                        name = "",
                        description = "",
                        currency = defaultCurrency,
                        currencies = currencies,
                        members = PREVIEW_MEMBERS,
                        isEditing = false,
                    )
                    is WorkspaceCreationState.Content -> copy(
                        currencies = currencies,
                        currency = currency.ifBlank { defaultCurrency },
                    )
                }
            }
        }
    }

    private fun saveWorkspace() {
        val state = currentState as? WorkspaceCreationState.Content ?: return
        if (state.name.isBlank()) return
        if (state.isSaving) return
        launch(
            onError = { err ->
                log.w(err) { "[save] unexpected exception" }
                updateState {
                    WorkspaceCreationState.content.modify(this) {
                        it.copy(isSaving = false, error = WorkspaceCreationError.Unknown)
                    }
                }
            },
        ) {
            updateState {
                WorkspaceCreationState.content.modify(this) {
                    it.copy(isSaving = true, error = null)
                }
            }
            val existingId = state.workspaceId
            if (existingId != null) {
                val existing = workspaceRepository.getById(existingId) ?: run {
                    updateState {
                        WorkspaceCreationState.content.modify(this) {
                            it.copy(isSaving = false, error = WorkspaceCreationError.Unknown)
                        }
                    }
                    return@launch
                }
                workspaceRepository.update(
                    existing.copy(
                        name = state.name.trim(),
                        description = state.description.trim(),
                        baseCurrency = CurrencyCode(state.currency),
                    ),
                )
                postSideEffect(WorkspaceCreationEffect.NavigateBack)
            } else {
                createWorkspace(
                    CreateWorkspaceUseCase.Params(
                        name = state.name,
                        description = state.description,
                        baseCurrency = CurrencyCode(state.currency),
                    ),
                ).fold(
                    ifLeft = { err ->
                        val mapped = err.toUi()
                        log.w { "[save] failed -> $mapped" }
                        updateState {
                            WorkspaceCreationState.content.modify(this) {
                                it.copy(isSaving = false, error = mapped)
                            }
                        }
                    },
                    ifRight = {
                        log.i { "[save] ok wid=${it.value}" }
                        postSideEffect(WorkspaceCreationEffect.NavigateBack)
                    },
                )
            }
        }
    }

    private companion object {
        const val TAG = "WorkspaceCreationVM"
    }
}

private fun CreateWorkspaceError.toUi(): WorkspaceCreationError = when (this) {
    is CreateWorkspaceError.NoCurrentUser -> WorkspaceCreationError.Unknown
    is CreateWorkspaceError.LocalWriteFailed -> WorkspaceCreationError.Unknown
    is CreateWorkspaceError.RemoteSyncFailed -> WorkspaceCreationError.RemoteSyncFailed
}

@optics
sealed interface WorkspaceCreationState {
    data object Loading : WorkspaceCreationState

    @optics
    data class Content(
        val workspaceId: WorkspaceId?,
        val name: String,
        val description: String,
        val currency: String,
        val currencies: List<Currency>,
        val members: List<WorkspaceMemberUi>,
        val isEditing: Boolean,
        val isSaving: Boolean = false,
        val error: WorkspaceCreationError? = null,
    ) : WorkspaceCreationState {
        companion object
    }

    companion object
}

enum class WorkspaceCreationError {
    /** Remote `syncWorkspace` push failed — workspace exists locally but isn't synced. */
    RemoteSyncFailed,

    /** Catch-all for unclassified failures (no current user, local Room write blew up, etc.). */
    Unknown,
}

data class WorkspaceMemberUi(
    val name: String,
    val role: WorkspaceRole,
    val initial: String,
)

sealed interface WorkspaceCreationEvent {
    data class OnNameChanged(val name: String) : WorkspaceCreationEvent
    data class OnDescriptionChanged(val description: String) : WorkspaceCreationEvent
    data class OnCurrencyChanged(val currency: String) : WorkspaceCreationEvent
    data object OnInviteMemberClick : WorkspaceCreationEvent
    data object OnSaveClick : WorkspaceCreationEvent
    data object OnBackClick : WorkspaceCreationEvent
}

sealed interface WorkspaceCreationEffect {
    data object NavigateBack : WorkspaceCreationEffect
}

// Placeholder members — replace with real data once a members feed is wired for the
// creation/edit flow. The screen stays useful for design review in the meantime.
private val PREVIEW_MEMBERS = listOf(
    WorkspaceMemberUi("Kasia Nowak", WorkspaceRole.OWNER, "K"),
    WorkspaceMemberUi("Marek N.", WorkspaceRole.EDITOR, "M"),
)
