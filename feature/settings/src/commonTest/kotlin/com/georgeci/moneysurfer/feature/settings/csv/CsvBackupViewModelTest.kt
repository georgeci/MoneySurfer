package com.georgeci.moneysurfer.feature.settings.csv

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.csv.CsvRowIssue
import com.georgeci.moneysurfer.domain.csv.ExportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.ImportTransactionsUseCase
import com.georgeci.moneysurfer.domain.csv.TransactionCsvCodec
import com.georgeci.moneysurfer.domain.csv.TransactionCsvColumn
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.Buffer

@OptIn(ExperimentalCoroutinesApi::class)
class CsvBackupViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun viewModel(existing: List<Transaction> = emptyList()): CsvBackupViewModel {
        val transactionRepository = SimpleTransactionRepository(existing)
        val accountRepository = SimpleAccountRepository()
        val applyTransactionChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)
        return CsvBackupViewModel(
            exportTransactions = ExportTransactionsUseCase(transactionRepository),
            importTransactions = ImportTransactionsUseCase(
                transactionRepository = transactionRepository,
                accountRepository = accountRepository,
                categoryRepository = SimpleCategoryRepository(),
                workspaceRepository = SimpleWorkspaceRepository(),
                createTransaction = CreateTransactionUseCase(applyTransactionChange),
                applyTransactionChange = applyTransactionChange,
            ),
            clock = ClockUseCase(),
        )
    }

    fun csvOf(vararg rows: List<String>): String = buildString {
        append(TransactionCsvCodec.header.joinToString(","))
        append('\n')
        rows.forEach { fields ->
            append(fields.joinToString(","))
            append('\n')
        }
    }

    "successful import stores the report in state and posts an ImportFinished notice" {
        runTest {
            val vm = viewModel()
            val csv = csvOf(TransactionCsvCodec.encode(aTransaction(id = transactionId("t-1"))))

            vm.sideEffects.effectFlow.test {
                vm.onEvent(CsvBackupEvent.OnOpenSourceChosen(Buffer().writeUtf8(csv)))

                awaitItem() shouldBe CsvBackupEffect.Notify(
                    CsvBackupNotice.ImportFinished(imported = 1, skipped = 0, failed = 0),
                )
            }
            vm.value.phase shouldBe CsvBackupPhase.Idle
            vm.value.lastImportReport?.imported shouldBe 1
        }
    }

    "import errors are exposed with row numbers in the state report" {
        runTest {
            val vm = viewModel()
            val badRow = TransactionCsvCodec.encode(aTransaction(id = transactionId("t-bad")))
                .toMutableList()
                .also { it[TransactionCsvColumn.AmountMinor.ordinal] = "not-a-number" }
            val csv = csvOf(
                TransactionCsvCodec.encode(aTransaction(id = transactionId("t-ok"))),
                badRow,
            )

            vm.sideEffects.effectFlow.test {
                vm.onEvent(CsvBackupEvent.OnOpenSourceChosen(Buffer().writeUtf8(csv)))

                awaitItem() shouldBe CsvBackupEffect.Notify(
                    CsvBackupNotice.ImportFinished(imported = 1, skipped = 0, failed = 1),
                )
            }
            val report = vm.value.lastImportReport
            report?.errors?.single()?.rowNumber shouldBe 3
            report?.errors?.single()?.issue shouldBe CsvRowIssue.InvalidValue("amount_minor")
        }
    }

    "a file with a foreign header posts InvalidFile and returns to Idle" {
        runTest {
            val vm = viewModel()

            vm.sideEffects.effectFlow.test {
                vm.onEvent(
                    CsvBackupEvent.OnOpenSourceChosen(
                        Buffer().writeUtf8("date,amount\n2024-01-01,12\n"),
                    ),
                )

                awaitItem() shouldBe CsvBackupEffect.Notify(CsvBackupNotice.InvalidFile)
            }
            vm.value.phase shouldBe CsvBackupPhase.Idle
            vm.value.lastImportReport shouldBe null
        }
    }

    "an oversized file posts FileTooLarge and returns to Idle" {
        runTest {
            val vm = viewModel()
            val chunk = "x".repeat(64 * 1024)
            val oversized = Buffer().apply { repeat(256 + 1) { writeUtf8(chunk) } }

            vm.sideEffects.effectFlow.test {
                vm.onEvent(CsvBackupEvent.OnOpenSourceChosen(oversized))

                awaitItem() shouldBe CsvBackupEffect.Notify(CsvBackupNotice.FileTooLarge)
            }
            vm.value.phase shouldBe CsvBackupPhase.Idle
            vm.value.lastImportReport shouldBe null
        }
    }

    "export posts ExportSuccess with the exported count" {
        runTest {
            val vm = viewModel(
                existing = listOf(
                    aTransaction(id = transactionId("t-1")),
                    aTransaction(id = transactionId("t-2")),
                ),
            )

            vm.sideEffects.effectFlow.test {
                vm.onEvent(CsvBackupEvent.OnSaveSinkChosen(Buffer()))

                awaitItem() shouldBe CsvBackupEffect.Notify(CsvBackupNotice.ExportSuccess(2))
            }
            vm.value.phase shouldBe CsvBackupPhase.Idle
        }
    }

    "export click requests a save file with a csv filename" {
        runTest {
            val vm = viewModel()

            vm.sideEffects.effectFlow.test {
                vm.onEvent(CsvBackupEvent.OnExportClick)

                val effect = awaitItem().shouldBeInstanceOf<CsvBackupEffect.RequestSaveFile>()
                effect.suggestedName.startsWith("moneysurfer-transactions-") shouldBe true
                effect.suggestedName.endsWith(".csv") shouldBe true
            }
        }
    }

    "cancelled picker keeps the screen idle" {
        runTest {
            val vm = viewModel()

            vm.onEvent(CsvBackupEvent.OnOpenSourceChosen(null))
            vm.onEvent(CsvBackupEvent.OnSaveSinkChosen(null))

            vm.value.phase shouldBe CsvBackupPhase.Idle
            vm.value.lastImportReport shouldBe null
        }
    }
})

// Minimal stubs — only the members the CSV use cases touch are functional.
private class SimpleTransactionRepository(initial: List<Transaction>) : TransactionRepository {
    private var store = initial.associateBy { it.id }

    /** Rows a delete tombstoned — out of every read, still there for a restore. */
    private val tombstones = mutableMapOf<TransactionId, Transaction>()

    override fun getAll(): Flow<List<Transaction>> = flowOf(store.values.toList())
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = flowOf(emptyList())
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = flowOf(emptyList())
    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flowOf(emptyList())
    override suspend fun getById(id: TransactionId): Transaction? = store[id]
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        store.values.filter { it.transferId == transferId }
    override suspend fun insert(transaction: Transaction) {
        store = store + (transaction.id to transaction)
    }
    override suspend fun update(transaction: Transaction) = insert(transaction)
    override suspend fun delete(id: TransactionId) {
        store[id]?.let { tombstones[id] = it }
        store = store - id
    }

    override suspend fun restore(id: TransactionId): Transaction? =
        tombstones.remove(id)?.also { store = store + (id to it) }
}

private class SimpleAccountRepository : AccountRepository {
    private val accounts = listOf(anAccount())

    override fun getAll(): Flow<List<Account>> = flowOf(accounts)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getById(id: AccountId): Account? = accounts.find { it.id == id }
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}

private class SimpleCategoryRepository : CategoryRepository {
    private val categories = listOf(aCategory())

    override fun getAll(): Flow<List<Category>> = flowOf(categories)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flowOf(categories)
    override suspend fun getById(id: CategoryId): Category? = categories.find { it.id == id }
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}

private class SimpleWorkspaceRepository : WorkspaceRepository {
    private val workspaces = listOf(aWorkspace())

    override fun getAll(): Flow<List<Workspace>> = flowOf(workspaces)
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = flowOf(workspaces)
    override suspend fun getById(id: WorkspaceId): Workspace? = workspaces.find { it.id == id }
    override suspend fun insert(workspace: Workspace) = Unit
    override suspend fun update(workspace: Workspace) = Unit
    override suspend fun delete(id: WorkspaceId) = Unit
}
