package com.georgeci.moneysurfer.domain.csv

/**
 * The column layout of a transactions CSV that this codec wrote at some point.
 *
 * The header row *is* the format version: a file's header names its columns in
 * order, so [forHeader] maps it back to the layout it was written with and the
 * decoder reads that layout — no version marker, no per-row `schema_version`
 * column, nothing outside the CSV grid a spreadsheet would mangle.
 *
 * Every layout stays listed here forever, and each one keeps its own exact
 * field count: a record is still rejected unless it carries every column of
 * *its* file's format, so a truncated row is rejected in a [V1] file and in a
 * [V2] file alike. Adding columns therefore means adding an entry here, never
 * relaxing the length check into "missing tail fields default to empty".
 */
enum class TransactionCsvFormat(val columns: List<TransactionCsvColumn>) {

    /** Pre-#260 exports: ends at `transfer_id`, before merchant/tags/recurring rule. */
    V1(V1_COLUMNS),

    /** #260's fields appended: merchant, tags, recurring rule. */
    V2(V2_COLUMNS),

    /** #399's `split_id` appended — what [TransactionCsvCodec.encode] writes today. */
    V3(V3_COLUMNS),
    ;

    val header: List<String> = columns.map { it.header }

    private val indexByColumn: Map<TransactionCsvColumn, Int> =
        columns.withIndex().associate { (index, column) -> column to index }

    /** Field index of [column] in this layout, or null when the layout predates it. */
    fun indexOf(column: TransactionCsvColumn): Int? = indexByColumn[column]

    companion object {
        /**
         * The newest layout — what every export writes and what [decode] assumes
         * when a caller has no header row to resolve ([TransactionCsvCodec.decode]).
         * Newest is the last entry,
         * so a future format only has to be declared, not registered here.
         */
        val Latest: TransactionCsvFormat = entries.last()

        /** The layout [header] was written with, or null when it is not one of ours. */
        fun forHeader(header: List<String>): TransactionCsvFormat? =
            entries.find { it.header == header }
    }
}

/**
 * Each layout's columns, frozen. Every list here describes bytes already sitting
 * in some user's backup file, so none of them may be re-derived from
 * `TransactionCsvColumn.entries`: that would silently re-define an old format
 * the day a column is added, and files written in it would stop matching their
 * own header. A new column means a new list appended below, leaving these as the
 * builds that wrote them left them.
 */
private val V1_COLUMNS: List<TransactionCsvColumn> = listOf(
    TransactionCsvColumn.Id,
    TransactionCsvColumn.WorkspaceId,
    TransactionCsvColumn.AccountId,
    TransactionCsvColumn.Type,
    TransactionCsvColumn.Status,
    TransactionCsvColumn.AmountMinor,
    TransactionCsvColumn.Currency,
    TransactionCsvColumn.CategoryId,
    TransactionCsvColumn.Note,
    TransactionCsvColumn.OperationAt,
    TransactionCsvColumn.OperationDate,
    TransactionCsvColumn.CreatedAt,
    TransactionCsvColumn.UpdatedAt,
    TransactionCsvColumn.TransferId,
)

private val V2_COLUMNS: List<TransactionCsvColumn> = V1_COLUMNS + listOf(
    TransactionCsvColumn.Merchant,
    TransactionCsvColumn.Tags,
    TransactionCsvColumn.RecurringRuleId,
)

private val V3_COLUMNS: List<TransactionCsvColumn> = V2_COLUMNS + listOf(
    TransactionCsvColumn.SplitId,
)
