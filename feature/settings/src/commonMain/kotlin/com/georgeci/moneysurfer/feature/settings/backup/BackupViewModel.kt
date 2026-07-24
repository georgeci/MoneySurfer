package com.georgeci.moneysurfer.feature.settings.backup

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.backup.BackupError
import com.georgeci.moneysurfer.domain.backup.BackupExporter
import com.georgeci.moneysurfer.domain.backup.BackupImporter
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.CancellationException
import okio.BufferedSink
import okio.BufferedSource
import okio.use
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class BackupViewModel(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val clock: ClockUseCase,
    offlineBuildFlags: OfflineBuildFlags,
) : MviViewModel<BackupState, BackupEvent, BackupEffect>(
    initialState = BackupState(isOffline = offlineBuildFlags.isOffline),
) {

    /** Passphrase chosen in the export dialog, held until the save picker returns a sink. */
    private var pendingExportPassphrase: String? = null

    /** Picked source of an encrypted backup, held while the passphrase prompt is up. */
    private var pendingImportSource: BufferedSource? = null

    override fun onEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnBackClick -> postSideEffect(BackupEffect.NavigateBack)

            BackupEvent.OnFrequencyClick -> postSideEffect(BackupEffect.OpenFrequencyPicker)
            BackupEvent.OnLocationClick -> postSideEffect(BackupEffect.OpenLocationPicker)
            BackupEvent.OnEncryptionClick -> postSideEffect(BackupEffect.OpenEncryptionScreen)
            BackupEvent.OnBackUpNowClick -> postSideEffect(BackupEffect.NotImplemented)

            BackupEvent.OnDownloadClick,
            BackupEvent.OnExportOptionsDismissed,
            is BackupEvent.OnExportOptionsConfirmed,
            is BackupEvent.OnSaveSinkChosen,
            -> onExportEvent(event)

            is BackupEvent.OnOpenSourceChosen,
            BackupEvent.OnImportPassphraseDismissed,
            is BackupEvent.OnImportPassphraseSubmitted,
            -> onImportEvent(event)

            BackupEvent.OnRestoreClick,
            BackupEvent.OnRestoreDismissed,
            BackupEvent.OnRestoreConfirmed,
            -> onRestoreEvent(event)

            BackupEvent.OnDeleteClick,
            BackupEvent.OnDeleteDismissed,
            BackupEvent.OnDeleteConfirmed,
            -> onDeleteEvent(event)
        }
    }

    override fun onCleared() {
        clearPendingImportSource()
        super.onCleared()
    }

    private fun onExportEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnDownloadClick ->
                updateState { copy(showExportOptions = true) }
            BackupEvent.OnExportOptionsDismissed -> {
                pendingExportPassphrase = null
                updateState { copy(showExportOptions = false) }
            }
            is BackupEvent.OnExportOptionsConfirmed -> {
                pendingExportPassphrase = event.passphrase?.takeIf { it.isNotEmpty() }
                updateState { copy(showExportOptions = false) }
                postSideEffect(BackupEffect.RequestSaveFile(suggestedFilename()))
            }
            is BackupEvent.OnSaveSinkChosen -> handleSaveSink(event.sink)
            else -> Unit
        }
    }

    private fun onImportEvent(event: BackupEvent) {
        when (event) {
            is BackupEvent.OnOpenSourceChosen -> handleOpenSource(event.source)
            BackupEvent.OnImportPassphraseDismissed -> {
                clearPendingImportSource()
                updateState { copy(showImportPassphrase = false) }
            }
            is BackupEvent.OnImportPassphraseSubmitted -> {
                val source = pendingImportSource
                pendingImportSource = null
                updateState { copy(showImportPassphrase = false) }
                if (source != null) runImport(source, event.passphrase)
            }
            else -> Unit
        }
    }

    private fun onRestoreEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnRestoreClick ->
                updateState { copy(showRestoreConfirmation = true) }
            BackupEvent.OnRestoreDismissed ->
                updateState { copy(showRestoreConfirmation = false) }
            BackupEvent.OnRestoreConfirmed -> {
                updateState { copy(showRestoreConfirmation = false) }
                postSideEffect(BackupEffect.RequestOpenFile)
            }
            else -> Unit
        }
    }

    private fun onDeleteEvent(event: BackupEvent) {
        when (event) {
            BackupEvent.OnDeleteClick ->
                updateState { copy(showDeleteConfirmation = true) }
            BackupEvent.OnDeleteDismissed ->
                updateState { copy(showDeleteConfirmation = false) }
            BackupEvent.OnDeleteConfirmed -> {
                updateState { copy(showDeleteConfirmation = false) }
                postSideEffect(BackupEffect.NotImplemented)
            }
            else -> Unit
        }
    }

    private fun handleSaveSink(sink: BufferedSink?) {
        val passphrase = pendingExportPassphrase
        pendingExportPassphrase = null
        if (sink == null) return
        updateState { copy(phase = BackupPhase.Exporting) }
        launch(
            onError = { error ->
                // MviViewModel.launch catches Exception (including cancellation);
                // rethrow so a cleared ViewModel doesn't surface as a fake error.
                if (error is CancellationException) throw error
                updateState { copy(phase = BackupPhase.Idle) }
                postSideEffect(BackupEffect.Notify(BackupNotice.fromError(error)))
                runCatching { sink.close() }
            },
        ) {
            sink.use { exporter.exportTo(it, passphrase).getOrThrow() }
            updateState { copy(phase = BackupPhase.Idle) }
            postSideEffect(BackupEffect.Notify(BackupNotice.ExportSuccess))
        }
    }

    private fun handleOpenSource(source: BufferedSource?) {
        if (source == null) return
        launch(
            onError = { error ->
                if (error is CancellationException) throw error
                postSideEffect(BackupEffect.Notify(BackupNotice.fromError(error)))
                runCatching { source.close() }
            },
        ) {
            if (importer.isPassphraseProtected(source)) {
                pendingImportSource = source
                updateState { copy(showImportPassphrase = true) }
            } else {
                runImport(source, passphrase = null)
            }
        }
    }

    private fun runImport(source: BufferedSource, passphrase: String?) {
        updateState { copy(phase = BackupPhase.Importing) }
        launch(
            onError = { error ->
                if (error is CancellationException) throw error
                updateState { copy(phase = BackupPhase.Idle) }
                postSideEffect(BackupEffect.Notify(BackupNotice.fromError(error)))
                runCatching { source.close() }
            },
        ) {
            source.use { importer.importFrom(it, passphrase).getOrThrow() }
            postSideEffect(BackupEffect.RestartApp)
        }
    }

    private fun clearPendingImportSource() {
        pendingImportSource?.let { source -> runCatching { source.close() } }
        pendingImportSource = null
    }

    private fun suggestedFilename(): String {
        val now = clock.now().toEpochMilliseconds()
        return "moneysurfer-backup-$now.zip"
    }
}

data class BackupState(
    val showDeleteConfirmation: Boolean = false,
    val showRestoreConfirmation: Boolean = false,
    val showExportOptions: Boolean = false,
    val showImportPassphrase: Boolean = false,
    val phase: BackupPhase = BackupPhase.Idle,
    val isOffline: Boolean = false,
) {
    /**
     * Scheduled cloud backups, "Back up now" and "Delete cloud backup" all talk to a
     * remote the offline build does not have. Hiding them leaves the local half —
     * export an archive, restore from one — which [BackupExporter] and [BackupImporter]
     * serve entirely from the on-device database.
     */
    val showCloudBackup: Boolean get() = !isOffline
}

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
    data object PassphraseRequired : BackupNotice
    data object WrongPassphrase : BackupNotice
    data object Generic : BackupNotice

    companion object {
        fun fromError(error: Throwable): BackupNotice = when (error) {
            is BackupError.PickerCancelled -> Cancelled
            is BackupError.SchemaMismatch -> SchemaMismatch(error.expected, error.actual)
            is BackupError.FormatMismatch -> FormatMismatch(error.expected, error.actual)
            is BackupError.InvalidArchive -> InvalidArchive
            is BackupError.MissingFile -> MissingFile(error.name)
            is BackupError.CrcMismatch -> Corrupted
            is BackupError.CorruptDatabase -> Corrupted
            is BackupError.PassphraseRequired -> PassphraseRequired
            is BackupError.WrongPassphrase -> WrongPassphrase
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
    data object OnExportOptionsDismissed : BackupEvent
    data class OnExportOptionsConfirmed(val passphrase: String?) : BackupEvent
    data class OnSaveSinkChosen(val sink: BufferedSink?) : BackupEvent
    data object OnRestoreClick : BackupEvent
    data object OnRestoreConfirmed : BackupEvent
    data object OnRestoreDismissed : BackupEvent
    data class OnOpenSourceChosen(val source: BufferedSource?) : BackupEvent
    data object OnImportPassphraseDismissed : BackupEvent
    data class OnImportPassphraseSubmitted(val passphrase: String) : BackupEvent
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
