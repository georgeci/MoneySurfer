package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTags
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Every column the transactions CSV has ever had. Declaration order is the
 * order [TransactionCsvFormat.Latest] writes them in, so ordinal == field index
 * in a record this build *writes*. A record being *read* may come from an older
 * layout that holds fewer columns — index those through
 * [TransactionCsvFormat.indexOf] instead.
 */
enum class TransactionCsvColumn(val header: String) {
    Id("id"),
    WorkspaceId("workspace_id"),
    AccountId("account_id"),
    Type("type"),
    Status("status"),
    AmountMinor("amount_minor"),
    Currency("currency"),
    CategoryId("category_id"),
    Note("note"),
    OperationAt("operation_at"),
    OperationDate("operation_date"),
    CreatedAt("created_at"),
    UpdatedAt("updated_at"),
    TransferId("transfer_id"),
    Merchant("merchant"),
    Tags("tags"),
    RecurringRuleId("recurring_rule_id"),
    SplitId("split_id"),
}

sealed interface TransactionCsvDecodeResult {
    data class Decoded(val transaction: Transaction) : TransactionCsvDecodeResult
    data class Rejected(val issue: CsvRowIssue) : TransactionCsvDecodeResult
}

/**
 * Maps a [Transaction] to/from one CSV record. Amounts are minor units,
 * instants are ISO-8601 strings, `operation_date` is an ISO local date, and
 * nullable ids serialise as empty fields. Purely structural — reference
 * validation (does the account exist?) happens in the import use case.
 *
 * Writing is always [TransactionCsvFormat.Latest]; reading accepts any layout
 * this codec has published, which the caller resolves from the file's header
 * row via [TransactionCsvFormat.forHeader] and passes to [decode].
 */
object TransactionCsvCodec {

    /** Header row of an export — the layout marker readers match on. */
    val header: List<String> = TransactionCsvFormat.Latest.header

    fun encode(transaction: Transaction): List<String> = with(transaction) {
        listOf(
            id.value,
            workspaceId.value,
            accountId.value,
            type.name,
            status.name,
            money.minor.toString(),
            currencyCode.value,
            categoryId?.value.orEmpty(),
            // Free text: neutralise CSV formula injection before it reaches a
            // spreadsheet. Reversed by unguardFormula on decode.
            Csv.guardFormula(note),
            operationAt.toString(),
            operationDate.toString(),
            createdAt.toString(),
            updatedAt.toString(),
            transferId?.value.orEmpty(),
            // Free text like the note, and guarded the same way.
            Csv.guardFormula(merchant),
            // Each tag is guarded on its own: any of them can be the one the
            // spreadsheet would evaluate once a user re-orders the cell by hand.
            Csv.encodeCellList(tags.map(Csv::guardFormula)),
            recurringRuleId?.value.orEmpty(),
            splitId?.value.orEmpty(),
        )
    }

    /**
     * Decodes one record read from a [format] file. Rejects anything whose field
     * count is not exactly that format's — the decoder's only integrity check,
     * and it holds per format, so a row missing its tail is still rejected
     * rather than back-filled with defaults.
     */
    fun decode(
        fields: List<String>,
        format: TransactionCsvFormat = TransactionCsvFormat.Latest,
    ): TransactionCsvDecodeResult {
        if (fields.size != format.columns.size) {
            return TransactionCsvDecodeResult.Rejected(
                CsvRowIssue.ColumnCountMismatch(
                    expected = format.columns.size,
                    actual = fields.size,
                ),
            )
        }
        return FieldReader(fields, format).decodeTransaction()
    }

    private class FieldReader(
        private val fields: List<String>,
        private val format: TransactionCsvFormat,
    ) {

        fun decodeTransaction(): TransactionCsvDecodeResult = try {
            TransactionCsvDecodeResult.Decoded(
                Transaction(
                    id = TransactionId(required(TransactionCsvColumn.Id)),
                    workspaceId = WorkspaceId(required(TransactionCsvColumn.WorkspaceId)),
                    accountId = AccountId(required(TransactionCsvColumn.AccountId)),
                    money = Money(amountMinor(TransactionCsvColumn.AmountMinor)),
                    currencyCode = CurrencyCode(required(TransactionCsvColumn.Currency)),
                    categoryId = optional(TransactionCsvColumn.CategoryId)?.let(::CategoryId),
                    note = Csv.unguardFormula(raw(TransactionCsvColumn.Note)),
                    merchant = Csv.unguardFormula(raw(TransactionCsvColumn.Merchant)),
                    tags = tags(TransactionCsvColumn.Tags),
                    operationAt = instant(TransactionCsvColumn.OperationAt),
                    operationDate = localDate(TransactionCsvColumn.OperationDate),
                    type = enum<TransactionType>(TransactionCsvColumn.Type),
                    status = enum<TransactionStatus>(TransactionCsvColumn.Status),
                    createdAt = instant(TransactionCsvColumn.CreatedAt),
                    updatedAt = instant(TransactionCsvColumn.UpdatedAt),
                    transferId = optional(TransactionCsvColumn.TransferId)?.let(::TransferId),
                    recurringRuleId = optional(TransactionCsvColumn.RecurringRuleId)
                        ?.let(::RecurringRuleId),
                    splitId = optional(TransactionCsvColumn.SplitId)?.let(::SplitId),
                ),
            )
        } catch (rejected: FieldRejectedException) {
            TransactionCsvDecodeResult.Rejected(CsvRowIssue.InvalidValue(rejected.column.header))
        }

        /**
         * The record's value for [column], or `""` when [format] predates the
         * column. Empty is already the "not recorded" encoding of every column
         * added after [TransactionCsvFormat.V1] (blank merchant, no tags, null
         * id), and [required] still rejects a blank field, so an older layout
         * can never quietly satisfy a mandatory column.
         */
        private fun raw(column: TransactionCsvColumn): String {
            val index = format.indexOf(column) ?: return ""
            return fields[index]
        }

        private fun required(column: TransactionCsvColumn): String =
            raw(column).ifBlank { throw FieldRejectedException(column) }

        private fun optional(column: TransactionCsvColumn): String? =
            raw(column).ifBlank { null }

        /**
         * Unpacks the tag cell and re-normalises it. The cell can come from a
         * hand-edited or hostile file, and [TransactionTags.normalize] is what
         * stops a comma — the separator of the Room column these land in — or a
         * 500-tag list from reaching storage. Tags this codec wrote are already
         * normalised and normalisation is idempotent, so the round-trip is
         * unaffected.
         */
        private fun tags(column: TransactionCsvColumn): List<String> =
            TransactionTags.normalize(
                Csv.decodeCellList(raw(column)).map(Csv::unguardFormula),
            )

        private fun long(column: TransactionCsvColumn): Long =
            raw(column).toLongOrNull() ?: throw FieldRejectedException(column)

        /**
         * A monetary minor amount bounded to the domain cap. Rejects crafted
         * out-of-range values (e.g. [Long.MIN_VALUE], whose abs() would wrap)
         * before they enter a [Money] and corrupt a cached balance. See #159.
         */
        private fun amountMinor(column: TransactionCsvColumn): Long {
            val value = long(column)
            if (value < -Money.MAX_MINOR || value > Money.MAX_MINOR) {
                throw FieldRejectedException(column)
            }
            return value
        }

        private fun instant(column: TransactionCsvColumn): Instant = try {
            Instant.parse(required(column))
        } catch (_: IllegalArgumentException) {
            throw FieldRejectedException(column)
        }

        private fun localDate(column: TransactionCsvColumn): LocalDate = try {
            LocalDate.parse(required(column))
        } catch (_: IllegalArgumentException) {
            throw FieldRejectedException(column)
        }

        private inline fun <reified E : Enum<E>> enum(column: TransactionCsvColumn): E =
            enumValues<E>().find { it.name == raw(column) }
                ?: throw FieldRejectedException(column)
    }

    private class FieldRejectedException(val column: TransactionCsvColumn) : RuntimeException()
}
