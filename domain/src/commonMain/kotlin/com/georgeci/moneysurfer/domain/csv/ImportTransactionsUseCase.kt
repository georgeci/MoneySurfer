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
        Result.failure(TransactionCsvError.Io(t))
    }

    private suspend fun runImport(source: BufferedSource): CsvImportReport {
        val records = Csv.parseRecords(source.readUtf8())
        val headerRecord = records.firstOrNull() ?: throw TransactionCsvError.EmptyFile
        if (headerRecord.fields != TransactionCsvCodec.header) {
            throw TransactionCsvError.HeaderMismatch
        }
        return importRecords(records.drop(1), loadKnownIds())
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
                    known.transactionIds.add(result.transaction.id)
                    imported++
                }
            }
        }
        return CsvImportReport(imported = imported, skippedDuplicates = skipped, errors = errors)
    }

    private fun decodeRow(fields: List<String>, known: KnownIds): RowResult =
        when (val decoded = TransactionCsvCodec.decode(fields)) {
            is TransactionCsvDecodeResult.Rejected -> RowResult.Failed(decoded.issue)
            is TransactionCsvDecodeResult.Decoded -> {
                val transaction = decoded.transaction
                val referenceIssue = known.referenceIssue(transaction)
                when {
                    transaction.id in known.transactionIds -> RowResult.Duplicate
                    referenceIssue != null -> RowResult.Failed(referenceIssue)
                    else -> RowResult.Insert(transaction)
                }
            }
        }

    private sealed interface RowResult {
        data object Duplicate : RowResult
        data class Failed(val issue: CsvRowIssue) : RowResult
        data class Insert(val transaction: Transaction) : RowResult
    }

    private class KnownIds(
        val transactionIds: MutableSet<TransactionId>,
        val workspaceIds: Set<WorkspaceId>,
        val accountIds: Set<AccountId>,
        val categoryIds: Set<CategoryId>,
    ) {
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

    private suspend fun loadKnownIds(): KnownIds = KnownIds(
        transactionIds = transactionRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        workspaceIds = workspaceRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        accountIds = accountRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
        categoryIds = categoryRepository.getAll().first().mapTo(mutableSetOf()) { it.id },
    )
}
