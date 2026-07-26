package com.georgeci.moneysurfer.domain.csv

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout

/** The CSV a fresh export of [transactions] produces, ready to hand back to an import. */
internal suspend fun exportedCsv(vararg transactions: Transaction): String {
    val buffer = Buffer()
    CsvStack(transactions = transactions.toList()).export(buffer).getOrThrow()
    return buffer.readUtf8()
}

/**
 * Real use cases wired over in-memory fakes. Reference data (workspace,
 * account, category) defaults to the ids used by [aTransaction].
 */
internal class CsvStack(
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

/**
 * A [Source] that emits [total] bytes lazily, one chunk per read, without ever
 * holding them all in memory. [produced] records how many it actually handed
 * out so a test can assert the reader stopped early.
 */
internal class CountingSource(private val total: Long) : Source {
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

internal class FakeTransactionRepository(initial: List<Transaction>) : TransactionRepository {
    private val store = MutableStateFlow(initial.associateBy { it.id })

    fun stored(): List<Transaction> = store.value.values.toList()

    override fun getAll(): Flow<List<Transaction>> = store.map { it.values.toList() }
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        store.map { all -> all.values.filter { it.accountId == accountId } }
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = error("not used in CSV tests")
    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = error("not used in CSV tests")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> =
        store.map { all -> all.values.filter { it.workspaceId == workspaceId } }
    override suspend fun getById(id: TransactionId): Transaction? = store.value[id]
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        store.value.values.filter { it.transferId == transferId }
    override suspend fun insert(transaction: Transaction) {
        store.value += (transaction.id to transaction)
    }
    override suspend fun update(transaction: Transaction) = insert(transaction)
    override suspend fun delete(id: TransactionId) {
        store.value -= id
    }
}

internal class FakeAccountRepository(initial: List<Account>) : AccountRepository {
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
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val account = store.value[accountId] ?: return
        insert(account.copy(archived = archived))
    }
}

internal class FakeCategoryRepository(initial: List<Category>) : CategoryRepository {
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

internal class FakeWorkspaceRepository(initial: List<Workspace>) : WorkspaceRepository {
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
