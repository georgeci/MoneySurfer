package com.georgeci.moneysurfer.domain.csv

/**
 * Outcome of a CSV import. Rows that failed stay in [errors] with their
 * 1-based CSV row number (header is row 1, first data row is row 2) so the
 * user can locate them in a spreadsheet editor.
 */
data class CsvImportReport(
    val imported: Int,
    val skippedDuplicates: Int,
    val errors: List<CsvRowError>,
)

data class CsvRowError(val rowNumber: Int, val issue: CsvRowIssue)

/** Why a single CSV row could not be imported. Localised in the UI layer. */
sealed interface CsvRowIssue {
    data class ColumnCountMismatch(val expected: Int, val actual: Int) : CsvRowIssue
    data class InvalidValue(val column: String) : CsvRowIssue
    data class UnknownWorkspace(val workspaceId: String) : CsvRowIssue
    data class UnknownAccount(val accountId: String) : CsvRowIssue
    data class UnknownCategory(val categoryId: String) : CsvRowIssue
}

/**
 * File-level errors from [ExportTransactionsUseCase] / [ImportTransactionsUseCase].
 * Raw I/O throwables are wrapped at this boundary per AGENTS.md so callers
 * never see platform exception types.
 */
sealed class TransactionCsvError(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    data class Io(val ioCause: Throwable) :
        TransactionCsvError("I/O error: ${ioCause.message}", ioCause)
    data object EmptyFile : TransactionCsvError("CSV file has no header row")
    data object HeaderMismatch :
        TransactionCsvError("CSV header does not match the MoneySurfer transactions format")
}
