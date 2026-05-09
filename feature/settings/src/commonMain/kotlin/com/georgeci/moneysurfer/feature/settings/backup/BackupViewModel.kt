package com.georgeci.moneysurfer.feature.settings.backup

import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupExporter
import com.georgeci.moneysurfer.domain.backup.BackupImporter
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import okio.BufferedSink
import okio.BufferedSource
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupViewModel(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val clock: ClockUseCase,
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
                postSideEffect(BackupEffect.Notify(BackupNotice.fromError(error)))
                runCatching { sink.close() }
            },
        ) {
            sink.use { exporter.exportTo(it).getOrThrow() }
            updateState { copy(phase = BackupPhase.Idle) }
            postSideEffect(BackupEffect.Notify(BackupNotice.ExportSuccess))
        }
    }

    private fun handleOpenSource(source: BufferedSource?) {
        if (source == null) return
        updateState { copy(phase = BackupPhase.Importing) }
        launch(
            onError = { error ->
                updateState { copy(phase = BackupPhase.Idle) }
                postSideEffect(BackupEffect.Notify(BackupNotice.fromError(error)))
                runCatching { source.close() }
            },
        ) {
            source.use { importer.importFrom(it).getOrThrow() }
            postSideEffect(BackupEffect.RestartApp)
        }
    }

    private fun suggestedFilename(): String {
        val now = clock.now().toEpochMilliseconds()
        return "moneysurfer-backup-$now.zip"
    }
}

data class BackupState(
    val showDeleteConfirmation: Boolean = false,
    val showRestoreConfirmation: Boolean = false,
    val phase: BackupPhase = BackupPhase.Idle,
)

enum class BackupPhase { Idle, Exporting, Importing }

/**
 * User-facing notice raised by the backup flow. Resolution to localised
 * strings happens in the screen layer via Compose resources — keeping the
 * ViewModel free of platform string APIs.
 */
sealed interface BackupNotice {
    data object ExportSuccess : BackupNotice
    data object Cancelled : BackupNotice
    data class SchemaMismatch(val expected: Int, val actual: Int) : BackupNotice
    data class FormatMismatch(val expected: Int, val actual: Int) : BackupNotice
    data object InvalidArchive : BackupNotice
    data class MissingFile(val name: String) : BackupNotice
    data object Corrupted : BackupNotice
    data object Generic : BackupNotice

    companion object {
        fun fromError(error: Throwable): BackupNotice = when (error) {
            is BackupError.PickerCancelled -> Cancelled
            is BackupError.SchemaMismatch -> SchemaMismatch(error.expected, error.actual)
            is BackupError.FormatMismatch -> FormatMismatch(error.expected, error.actual)
            is BackupError.InvalidArchive -> InvalidArchive
            is BackupError.MissingFile -> MissingFile(error.name)
            is BackupError.CrcMismatch -> Corrupted
            else -> Generic
        }
    }
}

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
    data class Notify(val notice: BackupNotice) : BackupEffect
    data object NotImplemented : BackupEffect
}
