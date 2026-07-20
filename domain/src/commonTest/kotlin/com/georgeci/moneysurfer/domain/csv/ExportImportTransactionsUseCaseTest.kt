package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer

class ExportImportTransactionsUseCaseTest : StringSpec({

    suspend fun exportedCsv(vararg transactions: Transaction): String {
        val buffer = Buffer()
        Stack(transactions = transactions.toList()).export(buffer).getOrThrow()
        return buffer.readUtf8()
    }

    "export writes a header plus one record per transaction and returns the count" {
        runTest {
            val stack = Stack(
                transactions = listOf(
                    aTransaction(id = transactionId("t-1")),
                    aTransaction(id = transactionId("t-2"), note = "with, comma"),
                ),
            )
            val buffer = Buffer()

            stack.export(buffer).getOrThrow() shouldBe 2

            val lines = buffer.readUtf8().trimEnd().lines()
            lines.first() shouldBe Csv.encodeRecord(TransactionCsvCodec.header)
            lines.size shouldBe 3
        }
    }

    "round-trip: an exported file imports every transaction into an empty database" {
        runTest {
            val transactions = listOf(
                aTransaction(id = transactionId("t-1"), note = "milk, \"eggs\"\nand bread"),
                aTransaction(id = transactionId("t-2"), categoryId = null),
            )
            val csv = exportedCsv(*transactions.toTypedArray())
            val stack = Stack()

            val report = stack.import(csv).getOrThrow()

            report shouldBe CsvImportReport(imported = 2, skippedDuplicates = 0, errors = emptyList())
            stack.transactionRepository.stored() shouldContainExactlyInAnyOrder transactions
        }
    }

    "import skips rows whose id already exists locally" {
        runTest {
            val existing = aTransaction(id = transactionId("t-1"))
            val fresh = aTransaction(id = transactionId("t-2"))
            val csv = exportedCsv(existing, fresh)
            val stack = Stack(transactions = listOf(existing))

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.skippedDuplicates shouldBe 1
            report.errors shouldBe emptyList()
        }
    }

    "import skips duplicate ids inside the same file" {
        runTest {
            val transaction = aTransaction(id = transactionId("t-dup"))
            val row = Csv.encodeRecord(TransactionCsvCodec.encode(transaction))
            val csv = Csv.encodeRecord(TransactionCsvCodec.header) + "\n" + row + "\n" + row + "\n"
            val stack = Stack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.skippedDuplicates shouldBe 1
            stack.transactionRepository.stored().size shouldBe 1
        }
    }

    "malformed and invalid rows are reported with row numbers and do not stop the import" {
        runTest {
            val good = aTransaction(id = transactionId("t-good"))
            val header = Csv.encodeRecord(TransactionCsvCodec.header)
            val goodRow = Csv.encodeRecord(TransactionCsvCodec.encode(good))
            val badAmount = Csv.encodeRecord(
                TransactionCsvCodec.encode(aTransaction(id = transactionId("t-bad")))
                    .toMutableList()
                    .also { it[TransactionCsvColumn.AmountMinor.ordinal] = "not-a-number" },
            )
            val csv = "$header\ntoo,few,columns\n$badAmount\n$goodRow\n"
            val stack = Stack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 1
            report.errors shouldBe listOf(
                CsvRowError(
                    rowNumber = 2,
                    issue = CsvRowIssue.ColumnCountMismatch(
                        expected = TransactionCsvCodec.header.size,
                        actual = 3,
                    ),
                ),
                CsvRowError(rowNumber = 3, issue = CsvRowIssue.InvalidValue("amount_minor")),
            )
            stack.transactionRepository.stored().single().id shouldBe transactionId("t-good")
        }
    }

    "rows referencing unknown workspace account or category are reported per row" {
        runTest {
            val unknownWorkspace = aTransaction(
                id = transactionId("t-ws"),
                workspaceId = WorkspaceId("ws-missing"),
            )
            val unknownAccount = aTransaction(
                id = transactionId("t-acc"),
                accountId = AccountId("a-missing"),
            )
            val unknownCategory = aTransaction(
                id = transactionId("t-cat"),
                categoryId = CategoryId("c-missing"),
            )
            val csv = exportedCsv(unknownWorkspace, unknownAccount, unknownCategory)
            val stack = Stack()

            val report = stack.import(csv).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnknownWorkspace("ws-missing")),
                CsvRowError(3, CsvRowIssue.UnknownAccount("a-missing")),
                CsvRowError(4, CsvRowIssue.UnknownCategory("c-missing")),
            )
        }
    }

    "a row whose account belongs to a different workspace is rejected" {
        runTest {
            val stack = Stack(
                workspaces = listOf(aWorkspace(), aWorkspace(id = workspaceId("ws-2"))),
                accounts = listOf(
                    anAccount(),
                    anAccount(id = accountId("a-2"), workspaceId = workspaceId("ws-2")),
                ),
            )
            val foreignAccount = aTransaction(accountId = accountId("a-2"))

            val report = stack.import(exportedCsv(foreignAccount)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(
                    2,
                    CsvRowIssue.AccountWorkspaceMismatch(accountId = "a-2", workspaceId = "ws-1"),
                ),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a row whose category belongs to a different workspace is rejected" {
        runTest {
            val stack = Stack(
                workspaces = listOf(aWorkspace(), aWorkspace(id = workspaceId("ws-2"))),
                categories = listOf(
                    aCategory(),
                    aCategory(id = categoryId("c-2"), workspaceId = workspaceId("ws-2")),
                ),
            )
            val foreignCategory = aTransaction(categoryId = categoryId("c-2"))

            val report = stack.import(exportedCsv(foreignCategory)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(
                    2,
                    CsvRowIssue.CategoryWorkspaceMismatch(categoryId = "c-2", workspaceId = "ws-1"),
                ),
            )
        }
    }

    "a row whose currency differs from the account currency is rejected" {
        runTest {
            val stack = Stack()
            val wrongCurrency = aTransaction(currencyCode = EUR)

            val report = stack.import(exportedCsv(wrongCurrency)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.CurrencyMismatch(currency = "EUR", accountCurrency = "USD")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a consistent transfer pair imports both legs" {
        runTest {
            val stack = Stack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val outLeg = aTransaction(
                id = transactionId("t-out"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val inLeg = aTransaction(
                id = transactionId("t-in"),
                accountId = accountId("a-2"),
                type = TransactionType.INCOME,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(outLeg, inLeg)).getOrThrow()

            report shouldBe CsvImportReport(imported = 2, skippedDuplicates = 0, errors = emptyList())
        }
    }

    "a transfer leg without its pair row is rejected" {
        runTest {
            val stack = Stack()
            val loneLeg = aTransaction(transferId = TransferId("tr-1"))

            val report = stack.import(exportedCsv(loneLeg)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a transfer pair with two legs of the same type is rejected" {
        runTest {
            val stack = Stack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val first = aTransaction(
                id = transactionId("t-1"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val second = aTransaction(
                id = transactionId("t-2"),
                accountId = accountId("a-2"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(first, second)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
                CsvRowError(3, CsvRowIssue.UnpairedTransfer("tr-1")),
            )
        }
    }

    "a transfer leg pairs with its duplicate counterpart already in the database" {
        runTest {
            val transfer = TransferId("tr-1")
            val outLeg = aTransaction(
                id = transactionId("t-out"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val inLeg = aTransaction(
                id = transactionId("t-in"),
                accountId = accountId("a-2"),
                type = TransactionType.INCOME,
                transferId = transfer,
            )
            val stack = Stack(
                accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))),
                transactions = listOf(outLeg),
            )

            val report = stack.import(exportedCsv(outLeg, inLeg)).getOrThrow()

            report shouldBe CsvImportReport(imported = 1, skippedDuplicates = 1, errors = emptyList())
        }
    }

    "a transfer pair with one invalid leg does not import the surviving half" {
        runTest {
            val stack = Stack(accounts = listOf(anAccount(), anAccount(id = accountId("a-2"))))
            val transfer = TransferId("tr-1")
            val goodLeg = aTransaction(
                id = transactionId("t-good"),
                type = TransactionType.EXPENSE,
                transferId = transfer,
            )
            val badLeg = aTransaction(
                id = transactionId("t-bad"),
                accountId = accountId("a-2"),
                currencyCode = EUR,
                type = TransactionType.INCOME,
                transferId = transfer,
            )

            val report = stack.import(exportedCsv(goodLeg, badLeg)).getOrThrow()

            report.imported shouldBe 0
            report.errors shouldBe listOf(
                CsvRowError(2, CsvRowIssue.UnpairedTransfer("tr-1")),
                CsvRowError(3, CsvRowIssue.CurrencyMismatch(currency = "EUR", accountCurrency = "USD")),
            )
            stack.transactionRepository.stored() shouldBe emptyList()
        }
    }

    "a foreign CSV header fails the whole import" {
        runTest {
            val stack = Stack()

            val error = stack.import("date,amount,payee\n2024-01-01,12,ACME\n")
                .exceptionOrNull()

            error.shouldBeInstanceOf<TransactionCsvError.HeaderMismatch>()
        }
    }

    "an empty file fails the whole import" {
        runTest {
            val stack = Stack()

            stack.import("").exceptionOrNull()
                .shouldBeInstanceOf<TransactionCsvError.EmptyFile>()
        }
    }

    "an oversized source is rejected without being fully read into memory" {
        runTest {
            val stack = Stack()
            // A 1 GiB stream generated lazily on demand — if the guard read it
            // whole it would exhaust the heap. The assertions below prove it
            // stops just past the 16 MiB cap instead.
            val huge = CountingSource(total = 1L * 1024 * 1024 * 1024)

            val error = stack.import(huge.buffer()).exceptionOrNull()

            error.shouldBeInstanceOf<TransactionCsvError.FileTooLarge>()
            stack.transactionRepository.stored() shouldBe emptyList()
            // Read only a hair over the cap, never the full gigabyte.
            huge.produced shouldBeLessThan 17L * 1024 * 1024
        }
    }
})

/**
 * A [Source] that emits [total] bytes lazily, one chunk per read, without ever
 * holding them all in memory. [produced] records how many it actually handed
 * out so a test can assert the reader stopped early.
 */
private class CountingSource(private val total: Long) : Source {
    var produced = 0L
        private set

    private val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (produced >= total) return -1L
        val n = minOf(byteCount, chunk.size.toLong(), total - produced).toInt()
        sink.write(chunk, 0, n)
        produced += n
        return n.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE
    override fun close() = Unit
}

/**
 * Real use cases wired over in-memory fakes. Reference data (workspace,
 * account, category) defaults to the ids used by [aTransaction].
 */
private class Stack(
    workspaces: List<Workspace> = listOf(aWorkspace()),
    accounts: List<Account> = listOf(anAccount()),
    categories: List<Category> = listOf(aCategory()),
    transactions: List<Transaction> = emptyList(),
) {
    val transactionRepository = FakeTransactionRepository(transactions)
    private val accountRepository = FakeAccountRepository(accounts)

    private val exportUseCase = ExportTransactionsUseCase(transactionRepository)
    private val importUseCase = ImportTransactionsUseCase(
        transactionRepository = transactionRepository,
        accountRepository = accountRepository,
        categoryRepository = FakeCategoryRepository(categories),
        workspaceRepository = FakeWorkspaceRepository(workspaces),
        createTransaction = CreateTransactionUseCase(
            ApplyTransactionChangeUseCase(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
            ),
        ),
    )

    suspend fun export(buffer: Buffer) = exportUseCase(buffer)

    suspend fun import(csv: String) = importUseCase(Buffer().writeUtf8(csv))

    suspend fun import(source: BufferedSource) = importUseCase(source)
}

private class FakeTransactionRepository(initial: List<Transaction>) : TransactionRepository {
    private val store = MutableStateFlow(initial.associateBy { it.id })

    fun stored(): List<Transaction> = store.value.values.toList()

    override fun getAll(): Flow<List<Transaction>> = store.map { it.values.toList() }
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> =
        error("not used in CSV tests")
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        store.map { all -> all.values.filter { it.accountId == accountId } }
    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        error("not used in CSV tests")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> =
        store.map { all -> all.values.filter { it.workspaceId == workspaceId } }
    override suspend fun getById(id: TransactionId): Transaction? = store.value[id]
    override suspend fun insert(transaction: Transaction) {
        store.value += (transaction.id to transaction)
    }
    override suspend fun update(transaction: Transaction) = insert(transaction)
    override suspend fun delete(id: TransactionId) {
        store.value -= id
    }
}

private class FakeAccountRepository(initial: List<Account>) : AccountRepository {
    private val store = MutableStateFlow(initial.associateBy { it.id })

    override fun getAll(): Flow<List<Account>> = store.map { it.values.toList() }
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> =
        store.map { all -> all.values.filter { it.workspaceId == workspaceId } }
    override suspend fun getById(id: AccountId): Account? = store.value[id]
    override suspend fun insert(account: Account) {
        store.value += (account.id to account)
    }
    override suspend fun update(account: Account) = insert(account)
    override suspend fun delete(id: AccountId) {
        store.value -= id
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        val account = store.value[accountId] ?: return
        insert(account.copy(balance = account.balance + delta))
    }
    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        val account = store.value[accountId] ?: return
        insert(account.copy(balance = balance))
    }
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val account = store.value[accountId] ?: return
        insert(account.copy(archived = archived))
    }
}

private class FakeCategoryRepository(initial: List<Category>) : CategoryRepository {
    private val store = MutableStateFlow(initial.associateBy { it.id })

    override fun getAll(): Flow<List<Category>> = store.map { it.values.toList() }
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> =
        store.map { all -> all.values.filter { it.workspaceId == workspaceId } }
    override suspend fun getById(id: CategoryId): Category? = store.value[id]
    override suspend fun insert(category: Category) {
        store.value += (category.id to category)
    }
    override suspend fun update(category: Category) = insert(category)
    override suspend fun delete(id: CategoryId) {
        store.value -= id
    }
}

private class FakeWorkspaceRepository(initial: List<Workspace>) : WorkspaceRepository {
    private val store = MutableStateFlow(initial.associateBy { it.id })

    override fun getAll(): Flow<List<Workspace>> = store.map { it.values.toList() }
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> =
        store.map { all -> all.values.filter { it.ownerId == userId } }
    override suspend fun getById(id: WorkspaceId): Workspace? = store.value[id]
    override suspend fun insert(workspace: Workspace) {
        store.value += (workspace.id to workspace)
    }
    override suspend fun update(workspace: Workspace) = insert(workspace)
    override suspend fun delete(id: WorkspaceId) {
        store.value -= id
    }
}
