package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSource
import org.koin.core.annotation.Single

/**
 * Merges transactions from a CSV produced by [ExportTransactionsUseCase] into
 * the local database. Rows whose id already exists locally (or earlier in the
 * same file) are skipped as duplicates; rows that fail to parse or reference
 * unknown workspaces/accounts/categories are collected in
 * [CsvImportReport.errors] with their row number — one bad row never aborts
 * the rest of the file.
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
        if (headerRecord.fields != TransactionCsvCodec.header) {
            throw TransactionCsvError.HeaderMismatch
        }
        return importRecords(records.drop(1), loadKnownIds())
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

    private suspend fun importRecords(
        records: List<CsvRecord>,
        known: KnownIds,
    ): CsvImportReport {
        var imported = 0
        var skipped = 0
        val errors = mutableListOf<CsvRowError>()
        for (record in records) {
            when (val result = decodeRow(record.fields, known)) {
                RowResult.Duplicate -> skipped++
                is RowResult.Failed -> errors.add(CsvRowError(record.rowNumber, result.issue))
                is RowResult.Insert -> {
                    createTransaction(result.transaction)
                    known.importedIds.add(result.transaction.id)
                    imported++
                }
            }
        }
        return CsvImportReport(imported = imported, skippedDuplicates = skipped, errors = errors)
    }

    private suspend fun decodeRow(fields: List<String>, known: KnownIds): RowResult =
        when (val decoded = TransactionCsvCodec.decode(fields)) {
            is TransactionCsvDecodeResult.Rejected -> RowResult.Failed(decoded.issue)
            is TransactionCsvDecodeResult.Decoded -> {
                val transaction = decoded.transaction
                val referenceIssue = known.referenceIssue(transaction)
                when {
                    isDuplicate(transaction.id, known) -> RowResult.Duplicate
                    referenceIssue != null -> RowResult.Failed(referenceIssue)
                    else -> RowResult.Insert(transaction)
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

    private sealed interface RowResult {
        data object Duplicate : RowResult
        data class Failed(val issue: CsvRowIssue) : RowResult
        data class Insert(val transaction: Transaction) : RowResult
    }

    private class KnownIds(
        val workspaceIds: Set<WorkspaceId>,
        val accountIds: Set<AccountId>,
        val categoryIds: Set<CategoryId>,
    ) {
        /** Ids inserted during this import run (also catches in-file duplicates). */
        val importedIds: MutableSet<TransactionId> = mutableSetOf()

        fun referenceIssue(transaction: Transaction): CsvRowIssue? = when {
            transaction.workspaceId !in workspaceIds ->
                CsvRowIssue.UnknownWorkspace(transaction.workspaceId.value)
            transaction.accountId !in accountIds ->
                CsvRowIssue.UnknownAccount(transaction.accountId.value)
            transaction.categoryId != null && transaction.categoryId !in categoryIds ->
                CsvRowIssue.UnknownCategory(transaction.categoryId.value)
            else -> null
        }
    }

    // Workspaces, accounts, and categories are small tables; prefetching their
    // ids once beats a per-row lookup for each of the three references.
    private suspend fun loadKnownIds(): KnownIds = KnownIds(
        workspaceIds = workspaceRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        accountIds = accountRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        categoryIds = categoryRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
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
