package com.georgeci.moneysurfer.feature.settings.csv

import com.georgeci.moneysurfer.domain.csv.CsvImportReport
import com.georgeci.moneysurfer.domain.csv.ExportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.ImportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.TransactionCsvError
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.CancellationException
import okio.BufferedSink
import okio.BufferedSource
import okio.use
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CsvBackupViewModel(
    private val exportTransactions: ExportTransactionsUseCase,
    private val importTransactions: ImportTransactionsUseCase,
    private val clock: ClockUseCase,
) : MviViewModel<CsvBackupState, CsvBackupEvent, CsvBackupEffect>(initialState = CsvBackupState()) {

    override fun onEvent(event: CsvBackupEvent) {
        when (event) {
            CsvBackupEvent.OnBackClick -> postSideEffect(CsvBackupEffect.NavigateBack)
            CsvBackupEvent.OnExportClick ->
                postSideEffect(CsvBackupEffect.RequestSaveFile(suggestedFilename()))
            is CsvBackupEvent.OnSaveSinkChosen -> handleSaveSink(event.sink)
            CsvBackupEvent.OnImportClick -> postSideEffect(CsvBackupEffect.RequestOpenFile)
            is CsvBackupEvent.OnOpenSourceChosen -> handleOpenSource(event.source)
        }
    }

    private fun handleSaveSink(sink: BufferedSink?) {
        if (sink == null) return
        updateState { copy(phase = CsvBackupPhase.Exporting) }
        launch(
            onError = { error ->
                // MviViewModel.launch catches Exception (including cancellation);
                // rethrow so a cleared ViewModel doesn't surface as a fake error.
                if (error is CancellationException) throw error
                updateState { copy(phase = CsvBackupPhase.Idle) }
                postSideEffect(CsvBackupEffect.Notify(CsvBackupNotice.fromError(error)))
                runCatching { sink.close() }
            },
        ) {
            val count = sink.use { exportTransactions(it).getOrThrow() }
            updateState { copy(phase = CsvBackupPhase.Idle) }
            postSideEffect(CsvBackupEffect.Notify(CsvBackupNotice.ExportSuccess(count)))
        }
    }

    private fun handleOpenSource(source: BufferedSource?) {
        if (source == null) return
        updateState { copy(phase = CsvBackupPhase.Importing) }
        launch(
            onError = { error ->
                if (error is CancellationException) throw error
                updateState { copy(phase = CsvBackupPhase.Idle) }
                postSideEffect(CsvBackupEffect.Notify(CsvBackupNotice.fromError(error)))
                runCatching { source.close() }
            },
        ) {
            val report = source.use { importTransactions(it).getOrThrow() }
            updateState { copy(phase = CsvBackupPhase.Idle, lastImportReport = report) }
            postSideEffect(
                CsvBackupEffect.Notify(
                    CsvBackupNotice.ImportFinished(
                        imported = report.imported,
                        skipped = report.skippedDuplicates,
                        failed = report.errors.size,
                    ),
                ),
            )
        }
    }

    private fun suggestedFilename(): String {
        val now = clock.now().toEpochMilliseconds()
        return "moneysurfer-transactions-$now.csv"
    }
}

data class CsvBackupState(
    val phase: CsvBackupPhase = CsvBackupPhase.Idle,
    val lastImportReport: CsvImportReport? = null,
)

enum class CsvBackupPhase { Idle, Exporting, Importing }

/**
 * User-facing notice raised by the CSV flow. Resolution to localised strings
 * happens in the screen layer via Compose resources — keeping the ViewModel
 * free of platform string APIs.
 */
sealed interface CsvBackupNotice {
    data class ExportSuccess(val count: Int) : CsvBackupNotice
    data class ImportFinished(val imported: Int, val skipped: Int, val failed: Int) : CsvBackupNotice
    data object InvalidFile : CsvBackupNotice
    data object Generic : CsvBackupNotice

    companion object {
        fun fromError(error: Throwable): CsvBackupNotice = when (error) {
            is TransactionCsvError.EmptyFile,
            is TransactionCsvError.HeaderMismatch,
            -> InvalidFile
            else -> Generic
        }
    }
}

sealed interface CsvBackupEvent {
    data object OnBackClick : CsvBackupEvent
    data object OnExportClick : CsvBackupEvent
    data class OnSaveSinkChosen(val sink: BufferedSink?) : CsvBackupEvent
    data object OnImportClick : CsvBackupEvent
    data class OnOpenSourceChosen(val source: BufferedSource?) : CsvBackupEvent
}

sealed interface CsvBackupEffect {
    data object NavigateBack : CsvBackupEffect
    data class RequestSaveFile(val suggestedName: String) : CsvBackupEffect
    data object RequestOpenFile : CsvBackupEffect
    data class Notify(val notice: CsvBackupNotice) : CsvBackupEffect
}
