package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Column order of the transactions CSV. Ordinal == field index in a record. */
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
 */
object TransactionCsvCodec {

    val header: List<String> = TransactionCsvColumn.entries.map { it.header }

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
            note,
            operationAt.toString(),
            operationDate.toString(),
            createdAt.toString(),
            updatedAt.toString(),
            transferId?.value.orEmpty(),
        )
    }

    fun decode(fields: List<String>): TransactionCsvDecodeResult {
        if (fields.size != header.size) {
            return TransactionCsvDecodeResult.Rejected(
                CsvRowIssue.ColumnCountMismatch(expected = header.size, actual = fields.size),
            )
        }
        return FieldReader(fields).decodeTransaction()
    }

    private class FieldReader(private val fields: List<String>) {

        fun decodeTransaction(): TransactionCsvDecodeResult = try {
            TransactionCsvDecodeResult.Decoded(
                Transaction(
                    id = TransactionId(required(TransactionCsvColumn.Id)),
                    workspaceId = WorkspaceId(required(TransactionCsvColumn.WorkspaceId)),
                    accountId = AccountId(required(TransactionCsvColumn.AccountId)),
                    money = Money(long(TransactionCsvColumn.AmountMinor)),
                    currencyCode = CurrencyCode(required(TransactionCsvColumn.Currency)),
                    categoryId = optional(TransactionCsvColumn.CategoryId)?.let(::CategoryId),
                    note = fields[TransactionCsvColumn.Note.ordinal],
                    operationAt = instant(TransactionCsvColumn.OperationAt),
                    operationDate = localDate(TransactionCsvColumn.OperationDate),
                    type = enum<TransactionType>(TransactionCsvColumn.Type),
                    status = enum<TransactionStatus>(TransactionCsvColumn.Status),
                    createdAt = instant(TransactionCsvColumn.CreatedAt),
                    updatedAt = instant(TransactionCsvColumn.UpdatedAt),
                    transferId = optional(TransactionCsvColumn.TransferId)?.let(::TransferId),
                ),
            )
        } catch (rejected: FieldRejectedException) {
            TransactionCsvDecodeResult.Rejected(CsvRowIssue.InvalidValue(rejected.column.header))
        }

        private fun raw(column: TransactionCsvColumn): String = fields[column.ordinal]

        private fun required(column: TransactionCsvColumn): String =
            raw(column).ifBlank { throw FieldRejectedException(column) }

        private fun optional(column: TransactionCsvColumn): String? =
            raw(column).ifBlank { null }

        private fun long(column: TransactionCsvColumn): Long =
            raw(column).toLongOrNull() ?: throw FieldRejectedException(column)

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
