package com.georgeci.moneysurfer.feature.settings.backup

import com.georgeci.moneysurfer.data.backup.BackupError
import com.georgeci.moneysurfer.data.backup.BackupExporter
import com.georgeci.moneysurfer.data.backup.BackupImporter
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.datetime.Clock
import okio.BufferedSink
import okio.BufferedSource
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupViewModel(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
) : MviViewModel<BackupState, BackupEvent, BackupEffect>(initialState = BackupState()) {

    override fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnBackClick -> postSideEffect(BackupEffect.NavigateBack)

            BackupEvent.OnFrequencyClick -> postSideEffect(BackupEffect.OpenFrequencyPicker)
            BackupEvent.OnLocationClick -> postSideEffect(BackupEffect.OpenLocationPicker)
            BackupEvent.OnEncryptionClick -> postSideEffect(BackupEffect.OpenEncryptionScreen)
            BackupEvent.OnBackUpNowClick -> postSideEffect(BackupEffect.NotImplemented)

            BackupEvent.OnDownloadClick ->
                postSideEffect(BackupEffect.RequestSaveFile(suggestedFilename()))
            is BackupEvent.OnSaveSinkChosen -> handleSaveSink(event.sink)

            BackupEvent.OnRestoreClick ->
                updateState { copy(showRestoreConfirmation = true) }
            BackupEvent.OnRestoreDismissed ->
                updateState { copy(showRestoreConfirmation = false) }
            BackupEvent.OnRestoreConfirmed -> {
                updateState { copy(showRestoreConfirmation = false) }
                postSideEffect(BackupEffect.RequestOpenFile)
            }
            is BackupEvent.OnOpenSourceChosen -> handleOpenSource(event.source)

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

    private fun handleSaveSink(sink: BufferedSink?) {
        if (sink == null) return
        updateState { copy(phase = BackupPhase.Exporting) }
        launch(
            onError = { error ->
                updateState { copy(phase = BackupPhase.Idle) }
                postSideEffect(BackupEffect.ShowToast(error.message ?: "Backup failed"))
                runCatching { sink.close() }
            },
        ) {
            sink.use { exporter.exportTo(it).getOrThrow() }
            updateState { copy(phase = BackupPhase.Idle) }
            postSideEffect(BackupEffect.ShowToast("Backup saved"))
        }
    }

    private fun handleOpenSource(source: BufferedSource?) {
        if (source == null) return
        updateState { copy(phase = BackupPhase.Importing) }
        launch(
            onError = { error ->
                updateState { copy(phase = BackupPhase.Idle) }
                postSideEffect(BackupEffect.ShowToast(error.toMessage()))
                runCatching { source.close() }
            },
        ) {
            source.use { importer.importFrom(it).getOrThrow() }
            postSideEffect(BackupEffect.RestartApp)
        }
    }

    private fun suggestedFilename(): String {
        val now = Clock.System.now().toEpochMilliseconds()
        return "moneysurfer-backup-$now.zip"
    }
}

private fun Throwable.toMessage(): String = when (this) {
    is BackupError.SchemaMismatch -> message ?: "Database schema mismatch"
    is BackupError.FormatMismatch -> message ?: "Backup format mismatch"
    is BackupError.InvalidArchive -> message ?: "Invalid backup archive"
    is BackupError.MissingFile -> message ?: "Required file missing in backup"
    is BackupError.CrcMismatch -> message ?: "Backup is corrupted"
    is BackupError.Io -> message ?: "I/O error"
    is BackupError -> message ?: "Restore failed"
    else -> message ?: "Restore failed"
}

data class BackupState(
    val showDeleteConfirmation: Boolean = false,
    val showRestoreConfirmation: Boolean = false,
    val phase: BackupPhase = BackupPhase.Idle,
)

enum class BackupPhase { Idle, Exporting, Importing }

sealed interface BackupEvent {
    data object OnBackClick : BackupEvent
    data object OnFrequencyClick : BackupEvent
    data object OnLocationClick : BackupEvent
    data object OnEncryptionClick : BackupEvent
    data object OnBackUpNowClick : BackupEvent
    data object OnDownloadClick : BackupEvent
    data class OnSaveSinkChosen(val sink: BufferedSink?) : BackupEvent
    data object OnRestoreClick : BackupEvent
    data object OnRestoreConfirmed : BackupEvent
    data object OnRestoreDismissed : BackupEvent
    data class OnOpenSourceChosen(val source: BufferedSource?) : BackupEvent
    data object OnDeleteClick : BackupEvent
    data object OnDeleteConfirmed : BackupEvent
    data object OnDeleteDismissed : BackupEvent
}

sealed interface BackupEffect {
    data object NavigateBack : BackupEffect
    data object OpenFrequencyPicker : BackupEffect
    data object OpenLocationPicker : BackupEffect
    data object OpenEncryptionScreen : BackupEffect
    data class RequestSaveFile(val suggestedName: String) : BackupEffect
    data object RequestOpenFile : BackupEffect
    data object RestartApp : BackupEffect
    data class ShowToast(val text: String) : BackupEffect
    data object NotImplemented : BackupEffect
}
