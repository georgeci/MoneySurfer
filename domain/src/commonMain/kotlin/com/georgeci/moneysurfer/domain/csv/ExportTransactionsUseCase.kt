package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okio.BufferedSink
import org.koin.core.annotation.Single

/**
 * Streams every local transaction into [sink] as CSV (header + one record per
 * transaction, see [TransactionCsvCodec]). Returns the number of exported
 * transactions. Caller owns [sink] and must close it; the use case flushes
 * but does not close.
 */
@Single
class ExportTransactionsUseCase(
    private val transactionRepository: TransactionRepository,
) {

    suspend operator fun invoke(sink: BufferedSink): Result<Int> = try {
        Result.success(withContext(Dispatchers.IO) { writeCsv(sink) })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (
        @Suppress("TooGenericExceptionCaught") t: Throwable,
    ) {
        Result.failure(TransactionCsvError.Unexpected(t))
    }

    private suspend fun writeCsv(sink: BufferedSink): Int {
        val transactions = transactionRepository.getAll().first()
        sink.writeUtf8(Csv.encodeRecord(TransactionCsvCodec.header))
        sink.writeUtf8(NEWLINE)
        for (transaction in transactions) {
            sink.writeUtf8(Csv.encodeRecord(TransactionCsvCodec.encode(transaction)))
            sink.writeUtf8(NEWLINE)
        }
        sink.flush()
        return transactions.size
    }

    private companion object {
        const val NEWLINE = "\n"
    }
}
