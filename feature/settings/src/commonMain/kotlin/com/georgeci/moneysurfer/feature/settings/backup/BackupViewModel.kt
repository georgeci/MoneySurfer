package com.georgeci.moneysurfer.feature.settings.backup

import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupViewModel : MviViewModel<BackupState, BackupEvent, BackupEffect>(
    initialState = BackupState(),
) {

    override fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnBackClick -> postSideEffect(BackupEffect.NavigateBack)

            BackupEvent.OnFrequencyClick -> postSideEffect(BackupEffect.OpenFrequencyPicker)
            BackupEvent.OnLocationClick -> postSideEffect(BackupEffect.OpenLocationPicker)
            BackupEvent.OnEncryptionClick -> postSideEffect(BackupEffect.OpenEncryptionScreen)
            BackupEvent.OnRestoreClick -> postSideEffect(BackupEffect.NavigateToRestore)

            BackupEvent.OnBackUpNowClick -> postSideEffect(BackupEffect.NotImplemented)
            BackupEvent.OnDownloadClick -> postSideEffect(BackupEffect.NotImplemented)

            BackupEvent.OnDeleteClick ->
                updateState { copy(showDeleteConfirmation = true) }
            BackupEvent.OnDeleteDismissed ->
                updateState { copy(showDeleteConfirmation = false) }
            BackupEvent.OnDeleteConfirmed -> {
                updateState { copy(showDeleteConfirmation = false) }
                postSideEffect(BackupEffect.NotImplemented)
            }
        }
    }
}

data class BackupState(
    val showDeleteConfirmation: Boolean = false,
)

sealed interface BackupEvent {
    data object OnBackClick : BackupEvent
    data object OnFrequencyClick : BackupEvent
    data object OnLocationClick : BackupEvent
    data object OnEncryptionClick : BackupEvent
    data object OnBackUpNowClick : BackupEvent
    data object OnDownloadClick : BackupEvent
    data object OnRestoreClick : BackupEvent
    data object OnDeleteClick : BackupEvent
    data object OnDeleteConfirmed : BackupEvent
    data object OnDeleteDismissed : BackupEvent
}

sealed interface BackupEffect {
    data object NavigateBack : BackupEffect
    data object OpenFrequencyPicker : BackupEffect
    data object OpenLocationPicker : BackupEffect
    data object OpenEncryptionScreen : BackupEffect
    data object NavigateToRestore : BackupEffect
    data object NotImplemented : BackupEffect
}
