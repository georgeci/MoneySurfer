package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSource
import org.koin.core.annotation.Single

/**
 * Merges transactions from a CSV produced by [ExportTransactionsUseCase] — by
 * this build or by any earlier one, see [TransactionCsvFormat] — into the local
 * database. Rows whose id already exists locally (or earlier in the
 * same file) are skipped as duplicates; rows that fail to parse, reference
 * unknown workspaces/accounts/categories, cross workspace boundaries, carry a
 * currency other than their account's, or form an inconsistent transfer pair
 * are collected in [CsvImportReport.errors] with their row number — one bad
 * row never aborts the rest of the file.
 *
 * Inserts go through [CreateTransactionUseCase] so cached account balances
 * stay consistent and sync outbox entries are enqueued. Caller owns [source]
 * and must close it.
 */
@Single
class ImportTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val workspaceRepository: WorkspaceRepository,
    private val createTransaction: CreateTransactionUseCase,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
) {

    suspend operator fun invoke(source: BufferedSource): Result<CsvImportReport> = try {
        Result.success(withContext(Dispatchers.IO) { runImport(source) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (csvError: TransactionCsvError) {
        Result.failure(csvError)
    } catch (
        @Suppress("TooGenericExceptionCaught") t: Throwable,
    ) {
        Result.failure(TransactionCsvError.Unexpected(t))
    }

    private suspend fun runImport(source: BufferedSource): CsvImportReport {
        val records = Csv.parseRecords(readCapped(source))
        val headerRecord = records.firstOrNull() ?: throw TransactionCsvError.EmptyFile
        // The header row identifies the layout, so a backup written by an older
        // build still imports — its rows just carry fewer columns, and the
        // fields it never had decode to their "not recorded" defaults.
        val format = TransactionCsvFormat.forHeader(headerRecord.fields)
            ?: throw TransactionCsvError.HeaderMismatch
        return importRecords(records.drop(1), loadKnownIds(), format)
    }

    /**
     * Reads the whole file, but bounds the read first: `request(limit + 1)`
     * pulls from the source only until at least `limit + 1` bytes are buffered
     * (Okio fills in ~8 KiB segments, so it stops within one segment of the
     * threshold — never the whole file), and a `true` result means the file is
     * larger than the cap, so we reject without materialising it. This also
     * caps the giant single field an unclosed quote would otherwise produce —
     * it can never exceed the total limit.
     */
    private fun readCapped(source: BufferedSource): String {
        if (source.request(MAX_IMPORT_BYTES + 1)) {
            throw TransactionCsvError.FileTooLarge(MAX_IMPORT_BYTES)
        }
        return source.readUtf8()
    }

    // Decode-then-insert, in two passes: transfer pairing is a cross-row
    // property, so every row must be decoded before any insert decision.
    // Memory is bounded by the MAX_IMPORT_BYTES cap on the file itself.
    private suspend fun importRecords(
        records: List<CsvRecord>,
        known: KnownIds,
        format: TransactionCsvFormat,
    ): CsvImportReport {
        val rows = records.map { DecodedRow(it.rowNumber, decodeRow(it.fields, known, format)) }
        val unpaired = unpairedTransferIds(rows)
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<CsvRowError>()
        for ((rowNumber, result) in rows) {
            when (result) {
                is RowResult.Duplicate -> skipped++
                is RowResult.Failed -> errors.add(CsvRowError(rowNumber, result.issue))
                is RowResult.Insert -> {
                    val transferId = result.transaction.transferId
                    if (transferId != null && transferId in unpaired) {
                        errors.add(
                            CsvRowError(rowNumber, CsvRowIssue.UnpairedTransfer(transferId.value)),
                        )
                    } else {
                        importRow(result.transaction)
                        imported++
                    }
                }
            }
        }
        return CsvImportReport(imported = imported, skippedDuplicates = skipped, errors = errors)
    }

    /**
     * Brings one importable row into the database — by lifting its tombstone if the id is still
     * held by a deleted row, and by inserting it otherwise.
     *
     * The tombstone branch exists because a delete no longer frees the id (issue #346): a plain
     * insert would hit the primary key and fail the whole import. Restoring is also what the user
     * asked for — importing a backup that contains a row you deleted is a request to have it
     * back — and it is the same row the export was taken from, so nothing of the CSV's content is
     * lost by preferring the stored copy.
     */
    private suspend fun importRow(transaction: Transaction) {
        if (applyTransactionChange.restore(transaction.id) != null) return
        createTransaction(transaction)
    }

    /**
     * Transfer ids whose legs do not form a consistent pair in this file:
     * exactly two rows (unique by transaction id), one EXPENSE and one INCOME,
     * on two different accounts. Duplicate rows count via their in-file copy,
     * so re-importing a full export where one leg already exists still pairs
     * up. Pending rows carrying an unpaired id are rejected — importing a lone
     * leg would show a half-transfer in the UI and skew one balance (#160).
     */
    private fun unpairedTransferIds(rows: List<DecodedRow>): Set<TransferId> {
        val legsByTransfer = rows
            .mapNotNull { row ->
                when (val result = row.result) {
                    is RowResult.Duplicate -> result.transaction
                    is RowResult.Insert -> result.transaction
                    is RowResult.Failed -> null
                }
            }
            .distinctBy { it.id }
            .mapNotNull { transaction -> transaction.transferId?.let { it to transaction } }
            .groupBy({ it.first }, { it.second })
        return legsByTransfer.filterValues { !it.isConsistentTransferPair() }.keys
    }

    private fun List<Transaction>.isConsistentTransferPair(): Boolean =
        size == 2 &&
            mapTo(mutableSetOf()) { it.type } ==
            setOf(TransactionType.EXPENSE, TransactionType.INCOME) &&
            this[0].accountId != this[1].accountId

    private suspend fun decodeRow(
        fields: List<String>,
        known: KnownIds,
        format: TransactionCsvFormat,
    ): RowResult =
        when (val decoded = TransactionCsvCodec.decode(fields, format)) {
            is TransactionCsvDecodeResult.Rejected -> RowResult.Failed(decoded.issue)
            is TransactionCsvDecodeResult.Decoded -> {
                val transaction = decoded.transaction
                val referenceIssue = known.referenceIssue(transaction)
                when {
                    isDuplicate(transaction.id, known) -> RowResult.Duplicate(transaction)
                    referenceIssue != null -> RowResult.Failed(referenceIssue)
                    else -> {
                        known.importedIds.add(transaction.id)
                        RowResult.Insert(transaction)
                    }
                }
            }
        }

    /**
     * Duplicate = already inserted from this file, or already in the database.
     * The DB side is an indexed point lookup per row instead of prefetching
     * every transaction id — transactions is the one table that can be large.
     */
    private suspend fun isDuplicate(id: TransactionId, known: KnownIds): Boolean =
        id in known.importedIds || transactionRepository.getById(id) != null

    private data class DecodedRow(val rowNumber: Int, val result: RowResult)

    private sealed interface RowResult {
        data class Duplicate(val transaction: Transaction) : RowResult
        data class Failed(val issue: CsvRowIssue) : RowResult
        data class Insert(val transaction: Transaction) : RowResult
    }

    private class KnownIds(
        val workspaceIds: Set<WorkspaceId>,
        val accountsById: Map<AccountId, Account>,
        val categoriesById: Map<CategoryId, Category>,
    ) {
        /** Ids accepted for insert from this file (also catches in-file duplicates). */
        val importedIds: MutableSet<TransactionId> = mutableSetOf()

        /**
         * First reference problem of the row, or null if it is importable.
         * Beyond bare existence, the account and category must belong to the
         * row's `workspace_id` and the row's currency must equal the account's
         * — a crafted CSV could otherwise attach a transaction of workspace A
         * to an account of workspace B (or in the wrong currency) and silently
         * diverge cached balances and per-workspace reports (#160).
         */
        fun referenceIssue(transaction: Transaction): CsvRowIssue? = when {
            transaction.workspaceId !in workspaceIds ->
                CsvRowIssue.UnknownWorkspace(transaction.workspaceId.value)
            else -> accountIssue(transaction) ?: categoryIssue(transaction)
        }

        private fun accountIssue(transaction: Transaction): CsvRowIssue? {
            val account = accountsById[transaction.accountId]
                ?: return CsvRowIssue.UnknownAccount(transaction.accountId.value)
            return when {
                account.workspaceId != transaction.workspaceId ->
                    CsvRowIssue.AccountWorkspaceMismatch(
                        accountId = transaction.accountId.value,
                        workspaceId = transaction.workspaceId.value,
                    )
                transaction.currencyCode != account.currencyCode ->
                    CsvRowIssue.CurrencyMismatch(
                        currency = transaction.currencyCode.value,
                        accountCurrency = account.currencyCode.value,
                    )
                else -> null
            }
        }

        private fun categoryIssue(transaction: Transaction): CsvRowIssue? {
            val categoryId = transaction.categoryId ?: return null
            val category = categoriesById[categoryId]
                ?: return CsvRowIssue.UnknownCategory(categoryId.value)
            return when {
                category.workspaceId != transaction.workspaceId ->
                    CsvRowIssue.CategoryWorkspaceMismatch(
                        categoryId = categoryId.value,
                        workspaceId = transaction.workspaceId.value,
                    )
                else -> null
            }
        }
    }

    // Workspaces, accounts, and categories are small tables; prefetching them
    // once beats a per-row lookup for each of the three references.
    private suspend fun loadKnownIds(): KnownIds = KnownIds(
        workspaceIds = workspaceRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        accountsById = accountRepository.getAll().first().associateBy { it.id },
        categoriesById = categoryRepository.getAll().first().associateBy { it.id },
    )

    private companion object {
        /**
         * Hard cap on the CSV we read into memory. Import buffers the whole file
         * (see [runImport]); without a bound a multi-GB pick — accidental or a
         * hostile "backup" shared to the user — would OOM the app. A real
         * transactions export runs a few hundred bytes per row, so 16 MiB still
         * covers tens of thousands of transactions.
         */
        private const val MAX_IMPORT_BYTES = 16L * 1024 * 1024
    }
}
