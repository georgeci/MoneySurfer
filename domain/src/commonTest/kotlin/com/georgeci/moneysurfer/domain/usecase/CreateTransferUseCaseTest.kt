package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class CreateTransferUseCaseTest : StringSpec({

    val ws = workspaceId("ws-1")
    val from = anAccount(
        id = accountId("a-from"),
        workspaceId = ws,
        currencyCode = USD,
        balance = Money.fromMinor(100_000),
    )
    val to = anAccount(
        id = accountId("a-to"),
        workspaceId = ws,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.zero(),
    )

    "writes two linked legs sharing transferId, tagged with the workspace's Transfer category" {
        val transferCategory = aCategory(
            id = categoryId("cat-transfer"),
            workspaceId = ws,
            name = "Transfer",
            type = CategoryType.TRANSFER,
        ).copy(systemKind = CategorySystemKind.TRANSFER)
        val env = TransferEnv(seedCategories = listOf(transferCategory))

        env.useCase(
            CreateTransferUseCase.Params(
                from = from,
                to = to,
                fromMoney = Money.fromMinor(25_000),
                toMoney = Money.fromMinor(23_000),
                note = "Lidl",
                operationAt = testInstant,
                operationDate = testDate,
            ),
        )

        env.txStore.values shouldHaveSize 2
        val outLeg = env.txStore.values.single { it.accountId == from.id }
        val inLeg = env.txStore.values.single { it.accountId == to.id }
        outLeg.type shouldBe TransactionType.EXPENSE
        outLeg.money shouldBe Money.fromMinor(25_000)
        outLeg.currencyCode shouldBe USD
        outLeg.categoryId shouldBe transferCategory.id
        inLeg.type shouldBe TransactionType.INCOME
        inLeg.money shouldBe Money.fromMinor(23_000)
        inLeg.currencyCode shouldBe CurrencyCode("EUR")
        inLeg.categoryId shouldBe transferCategory.id
        outLeg.transferId.shouldNotBeNull()
        outLeg.transferId shouldBe inLeg.transferId
    }

    "creates the system Transfer category lazily when the workspace is missing it" {
        val env = TransferEnv(seedCategories = emptyList())

        env.useCase(
            CreateTransferUseCase.Params(
                from = from,
                to = to,
                fromMoney = Money.fromMinor(10_000),
                toMoney = Money.fromMinor(10_000),
                note = "",
                operationAt = testInstant,
                operationDate = testDate,
            ),
        )

        val seeded = env.categoryStore.values.single { it.systemKind == CategorySystemKind.TRANSFER }
        seeded.workspaceId shouldBe ws
        seeded.type shouldBe CategoryType.TRANSFER
        env.txStore.values.forEach { it.categoryId shouldBe seeded.id }
    }

    "rejects same-account transfer" {
        val env = TransferEnv(seedCategories = emptyList())

        shouldThrow<IllegalArgumentException> {
            env.useCase(
                CreateTransferUseCase.Params(
                    from = from,
                    to = from,
                    fromMoney = Money.fromMinor(1_000),
                    toMoney = Money.fromMinor(1_000),
                    note = "",
                    operationAt = testInstant,
                    operationDate = testDate,
                ),
            )
        }
        env.txStore.values shouldHaveSize 0
    }
})

private class TransferEnv(seedCategories: List<Category>) {
    val txStore = mutableMapOf<TransactionId, Transaction>()
    val categoryStore = mutableMapOf<CategoryId, Category>().apply {
        seedCategories.forEach { put(it.id, it) }
    }
    val balances = mutableMapOf<AccountId, Long>()

    private val txRepo = object : TransactionRepository {
        override fun getAll(): Flow<List<Transaction>> = flowOf(txStore.values.toList())
        override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
            flowOf(txStore.values.filter { it.accountId == accountId })
        override fun getCategorizedWindow(
            accountId: AccountId?,
            window: TransactionPeriodWindow,
            limit: Int,
        ): Flow<List<CategorizedTransaction>> = flowOf(emptyList())
        override fun getTotals(
            accountId: AccountId?,
            window: TransactionPeriodWindow,
        ): Flow<List<TransactionTotal>> = flowOf(emptyList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> =
            flowOf(txStore.values.filter { it.workspaceId == workspaceId })
        override suspend fun getById(id: TransactionId): Transaction? = txStore[id]
        override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
            txStore.values.filter { it.transferId == transferId }
        override suspend fun insert(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun update(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun delete(id: TransactionId) { txStore.remove(id) }
    }

    private val accRepo = object : AccountRepository {
        override fun getAll(): Flow<List<Account>> = flowOf(emptyList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getById(id: AccountId): Account? = null
        override suspend fun insert(account: Account) = Unit
        override suspend fun update(account: Account) = Unit
        override suspend fun delete(id: AccountId) = Unit
        override suspend fun applyDelta(accountId: AccountId, delta: Money) {
            balances[accountId] = (balances[accountId] ?: 0L) + delta.minor
        }
        override suspend fun setBalance(accountId: AccountId, balance: Money) {
            balances[accountId] = balance.minor
        }
        override suspend fun reorder(orderedIds: List<AccountId>) = Unit
        override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    }

    private val catRepo = object : CategoryRepository {
        override fun getAll(): Flow<List<Category>> = flowOf(categoryStore.values.toList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> =
            flowOf(categoryStore.values.filter { it.workspaceId == workspaceId })
        override suspend fun getById(id: CategoryId): Category? = categoryStore[id]
        override suspend fun insert(category: Category) { categoryStore[category.id] = category }
        override suspend fun update(category: Category) { categoryStore[category.id] = category }
        override suspend fun delete(id: CategoryId) { categoryStore.remove(id) }
    }

    private val applyChange = ApplyTransactionChangeUseCase(txRepo, accRepo)
    private val getCurrentTime = GetCurrentTimeUseCase(ClockUseCase())
    private val createTransfer = CreateTransferUseCase(catRepo, applyChange, getCurrentTime)

    suspend fun useCase(params: CreateTransferUseCase.Params) {
        createTransfer.invoke(params)
    }
}
