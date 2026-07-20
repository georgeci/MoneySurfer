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
    data class AccountWorkspaceMismatch(val accountId: String, val workspaceId: String) : CsvRowIssue
    data class CategoryWorkspaceMismatch(val categoryId: String, val workspaceId: String) : CsvRowIssue
    data class CurrencyMismatch(val currency: String, val accountCurrency: String) : CsvRowIssue
    data class UnpairedTransfer(val transferId: String) : CsvRowIssue
}

/**
 * File-level errors from [ExportTransactionsUseCase] / [ImportTransactionsUseCase].
 * Raw throwables (I/O and anything else that escapes the use cases) are
 * wrapped at this boundary per AGENTS.md so callers never see platform
 * exception types.
 */
sealed class TransactionCsvError(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {
    data class Unexpected(val unexpectedCause: Throwable) : TransactionCsvError(
        "CSV processing failed: " +
            (unexpectedCause.message ?: unexpectedCause::class.simpleName ?: "unknown cause"),
        unexpectedCause,
    )
    data object EmptyFile : TransactionCsvError("CSV file has no header row")
    data object HeaderMismatch :
        TransactionCsvError("CSV header does not match the MoneySurfer transactions format")

    /**
     * The picked file is larger than [ImportTransactionsUseCase] will read into
     * memory. Guards against an accidental or hostile multi-GB "backup" that
     * would otherwise OOM the app during import.
     */
    data class FileTooLarge(val limitBytes: Long) :
        TransactionCsvError("CSV file exceeds the $limitBytes-byte import limit")
}
