package com.georgeci.moneysurfer.feature.category.details

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryMonthlyTotal
import com.georgeci.moneysurfer.domain.model.CategorySpendHistory
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.CategorySpendRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendHistoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailsViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "starts in Loading before anything is emitted" {
        val env = Env()

        env.newViewModel(categoryId("food")).value
            .shouldBeInstanceOf<CategoryDetailsState.Loading>()
    }

    "reports Missing when the category is not in the workspace" {
        val env = Env(categories = listOf(aCategory(id = categoryId("other"))))

        env.newViewModel(categoryId("ghost")).value
            .shouldBeInstanceOf<CategoryDetailsState.Missing>()
    }

    "falls back to Missing when the category is deleted under an open screen" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val env = Env(categories = listOf(food))
        val viewModel = env.newViewModel(food.id)

        viewModel.value.shouldBeInstanceOf<CategoryDetailsState.Content>()
        env.setCategories(emptyList())

        viewModel.value.shouldBeInstanceOf<CategoryDetailsState.Missing>()
    }

    "the trend always spans the full six-month window" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val env = Env(categories = listOf(food))

        val content = env.newViewModel(food.id).content()

        content.months.size shouldBe CategorySpendHistory.TREND_MONTHS
    }

    "a leaf category is flagged as such and lists no subcategories" {
        val coffee = aCategory(id = categoryId("coffee"), name = "Coffee")
        val env = Env(categories = listOf(coffee))

        val content = env.newViewModel(coffee.id).content()

        content.isLeaf shouldBe true
        content.subcategories.shouldBeEmpty()
    }

    "a parent lists its children with the share each takes of the parent total" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val groceries = aCategory(id = categoryId("groceries"), name = "Groceries", parentId = food.id)
        val dining = aCategory(id = categoryId("dining"), name = "Dining", parentId = food.id)
        val env = Env(
            categories = listOf(food, groceries, dining),
            totalsFor = { month ->
                listOf(
                    monthlyTotal(groceries.id, month, 75_00),
                    monthlyTotal(dining.id, month, 25_00),
                )
            },
        )

        val content = env.newViewModel(food.id).content()

        content.isLeaf shouldBe false
        content.subcategories.map { it.name } shouldBe listOf("Groceries", "Dining")
        content.subcategories.map { it.share } shouldBe listOf(0.75f, 0.25f)
    }

    "a child's share is zero rather than a division by zero when the parent booked nothing" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val groceries = aCategory(id = categoryId("groceries"), name = "Groceries", parentId = food.id)
        val env = Env(categories = listOf(food, groceries))

        val content = env.newViewModel(food.id).content()

        content.subcategories.single().share shouldBe 0f
    }

    /**
     * The session pointer lives in preferences and is restored independently of the `workspaces`
     * row, so a device that has not pulled the workspace yet resolves no base currency — and the
     * aggregate query filters on it. The trend has to fill in when the row lands rather than stay
     * latched on the empty result for as long as the screen is open.
     */
    "a workspace that has not landed yet leaves the trend empty, then fills it in" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val env = Env(
            categories = listOf(food),
            totalsFor = { month -> listOf(monthlyTotal(food.id, month, 42_00)) },
            workspaceCurrency = null,
        )
        val viewModel = env.newViewModel(food.id)

        viewModel.content().months.last().minorValue shouldBe 0L

        env.setWorkspaceCurrency(USD)

        viewModel.content().months.last().minorValue shouldBe 42_00L
    }

    "changing the base currency re-points the trend at the rows that now count" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val env = Env(
            categories = listOf(food),
            totalsFor = { month -> listOf(monthlyTotal(food.id, month, 42_00)) },
        )
        val viewModel = env.newViewModel(food.id)

        viewModel.content().months.last().minorValue shouldBe 42_00L

        // The stored rows stay in USD — UpdateWorkspaceCurrencyUseCase rewrites accounts, not
        // transactions — so switching the workspace to EUR must empty the trend, the way budgets
        // already treat a foreign-currency expense.
        env.setWorkspaceCurrency(EUR)

        viewModel.content().months.last().minorValue shouldBe 0L
    }

    "recent transactions include the ones booked to a child" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val groceries = aCategory(id = categoryId("groceries"), name = "Groceries", parentId = food.id)
        val env = Env(
            categories = listOf(food, groceries),
            transactions = listOf(
                aTransaction(id = transactionId("t-1"), categoryId = food.id, note = "Market"),
                aTransaction(id = transactionId("t-2"), categoryId = groceries.id, note = "Bakery"),
                aTransaction(id = transactionId("t-3"), categoryId = categoryId("transport"), note = "Bus"),
            ),
        )

        val content = env.newViewModel(food.id).content()

        content.transactions.map { it.title } shouldBe listOf("Market", "Bakery")
    }

    "a leg of a split is flagged, because swiping it deletes the whole receipt" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val env = Env(
            categories = listOf(food),
            transactions = listOf(
                aTransaction(id = transactionId("t-1"), categoryId = food.id, note = "Market")
                    .copy(splitId = SplitId("sp-1")),
                aTransaction(id = transactionId("t-2"), categoryId = food.id, note = "Bus"),
            ),
        )

        val content = env.newViewModel(food.id).content()

        // This screen lists the legs separately on purpose — a leg is its own row under its own
        // category — but the delete is group-aware, so the confirmation has to be able to say so.
        content.transactions.single { it.id == transactionId("t-1") }.isSplitLeg shouldBe true
        content.transactions.single { it.id == transactionId("t-2") }.isSplitLeg shouldBe false
    }

    "swiping a transaction away removes it, and the snackbar's Undo puts it back" {
        runTest {
            val food = aCategory(id = categoryId("food"), name = "Food")
            val env = Env(
                categories = listOf(food),
                transactions = listOf(
                    aTransaction(id = transactionId("t-1"), categoryId = food.id, note = "Market"),
                    aTransaction(id = transactionId("t-2"), categoryId = food.id, note = "Bakery"),
                ),
            )
            val viewModel = env.newViewModel(food.id)

            viewModel.onEvent(CategoryDetailsEvent.OnDeleteTransaction(transactionId("t-2")))

            // Not an optimistic edit of the state — the row is gone because the query re-emitted.
            viewModel.content().transactions.map { it.title } shouldBe listOf("Market")

            env.snackbar.requests.first().onAction!!.invoke()

            viewModel.content().transactions.map { it.title } shouldBe listOf("Market", "Bakery")
        }
    }

    "OnEditClick asks to open the editor for this category" {
        val food = aCategory(id = categoryId("food"))
        val viewModel = Env(categories = listOf(food)).newViewModel(food.id)

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(CategoryDetailsEvent.OnEditClick)
            awaitItem() shouldBe CategoryDetailsEffect.NavigateToCategoryEdit(food.id)
        }
    }

    "OnTransactionClick opens the transaction it was raised for" {
        val food = aCategory(id = categoryId("food"))
        val viewModel = Env(categories = listOf(food)).newViewModel(food.id)

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(CategoryDetailsEvent.OnTransactionClick(transactionId("t-1")))
            awaitItem() shouldBe CategoryDetailsEffect.NavigateToTransactionDetails(transactionId("t-1"))
        }
    }

    "OnSubcategoryClick drills into the child rather than replacing this screen" {
        val food = aCategory(id = categoryId("food"))
        val groceries = aCategory(id = categoryId("groceries"), parentId = food.id)
        val viewModel = Env(categories = listOf(food, groceries)).newViewModel(food.id)

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(CategoryDetailsEvent.OnSubcategoryClick(groceries.id))
            awaitItem() shouldBe CategoryDetailsEffect.NavigateToCategoryDetails(groceries.id)
        }
    }
})

private fun CategoryDetailsViewModel.content(): CategoryDetailsState.Content =
    value.shouldBeInstanceOf<CategoryDetailsState.Content>()

private fun monthlyTotal(
    id: CategoryId,
    month: YearMonth,
    minor: Long,
    count: Int = 1,
) = CategoryMonthlyTotal(
    categoryId = id,
    month = month,
    total = Money.fromMinor(minor),
    transactionCount = count,
)

/**
 * Wires the real use cases over fake repositories, the way the app does.
 *
 * [totalsFor] receives the last month of the window the use case asked for, so a test can seed
 * "this month" without knowing today's date — the anchor comes from the real clock, and there is
 * no fake clock in this codebase to substitute.
 */
private class Env(
    categories: List<Category>? = null,
    private val transactions: List<Transaction> = emptyList(),
    private val totalsFor: (YearMonth) -> List<CategoryMonthlyTotal> = { emptyList() },
    workspaceCurrency: CurrencyCode? = USD,
) {
    private val workspace = workspaceId("ws-1")
    val snackbar = SnackbarController()

    // Replay-1 and deliberately unseeded when no categories are given, so the "still loading"
    // case is reachable: a StateFlow always has a value, which would skip Loading entirely.
    private val categoryFlow = MutableSharedFlow<List<Category>>(replay = 1)
    private var current: List<Category> = categories.orEmpty()

    // A null [workspaceCurrency] models a device whose session pointer already names a workspace
    // Room has not stored yet. The row can land later, so this is a flow rather than a fixed value.
    private val workspaceFlow = MutableStateFlow(workspacesWith(workspaceCurrency))

    init {
        if (categories != null) check(categoryFlow.tryEmit(categories)) { "failed to seed categories" }
    }

    fun setCategories(next: List<Category>) {
        current = next
        check(categoryFlow.tryEmit(next)) { "failed to emit categories" }
    }

    fun setWorkspaceCurrency(currency: CurrencyCode) {
        workspaceFlow.value = workspacesWith(currency)
    }

    private fun workspacesWith(currency: CurrencyCode?): List<Workspace> =
        currency?.let { listOf(aWorkspace(id = workspace, baseCurrency = it)) }.orEmpty()

    fun newViewModel(categoryId: CategoryId): CategoryDetailsViewModel {
        val session = InMemorySessionPointers(currentWorkspaceId = workspace)
        val categoryRepo = FakeCategoryRepository(
            flow = categoryFlow,
            snapshot = { current },
            write = ::setCategories,
        )
        val transactionRepo = FakeTransactionRepository(transactions)
        val spendRepo = FakeCategorySpendRepository(totalsFor, rowCurrency = USD)
        return CategoryDetailsViewModel(
            categoryId = categoryId,
            getCategories = GetCategoriesUseCase(categoryRepo, session),
            getCategorySpendHistory = GetCategorySpendHistoryUseCase(
                categoryRepository = categoryRepo,
                spendRepository = spendRepo,
                workspaceRepository = FakeWorkspaceRepository(workspaceFlow),
                session = session,
                clock = ClockUseCase(),
            ),
            getTransactionsByCategory = GetTransactionsByCategoryUseCase(
                transactionRepository = transactionRepo,
                categoryRepository = categoryRepo,
                session = session,
            ),
            deleteWithUndo = ApplyTransactionChangeUseCase(transactionRepo, BalanceOnlyAccountRepository)
                .let { applyChange ->
                    DeleteTransactionWithUndo(
                        deleteTransaction = DeleteTransactionUseCase(transactionRepo, applyChange),
                        restoreTransactions = RestoreTransactionsUseCase(applyChange),
                        snackbar = snackbar,
                    )
                },
        )
    }
}

/**
 * Deleting a transaction moves an account balance, so the write has to land somewhere — but this
 * screen never reads accounts, and the balances themselves are asserted in DeleteUndoIntegrationIT
 * against real Room. Anything beyond the delta means the test wandered off its subject.
 */
private object BalanceOnlyAccountRepository : AccountRepository {
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun getById(id: AccountId) = error("not used")
    override fun getAll(): Flow<List<Account>> = error("not used")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = error("not used")
    override suspend fun insert(account: Account) = error("not used")
    override suspend fun update(account: Account) = error("not used")
    override suspend fun delete(id: AccountId) = error("not used")
    override suspend fun setBalance(accountId: AccountId, balance: Money) = error("not used")
    override suspend fun reorder(orderedIds: List<AccountId>) = error("not used")
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = error("not used")
}

/**
 * Reproduces the aggregate query's currency filter rather than ignoring it: the seeded rows are all
 * denominated in [rowCurrency], so a query for any other base currency — or for none, on a device
 * that has not stored the workspace yet — comes back empty, exactly as the SQL
 * `currencyCode = :baseCurrency` term does.
 *
 * Without that, a regression that stopped threading the currency through would leave every spec in
 * this file green while the shipped trend rendered zeros.
 */
private class FakeCategorySpendRepository(
    private val totalsFor: (YearMonth) -> List<CategoryMonthlyTotal>,
    private val rowCurrency: CurrencyCode,
) : CategorySpendRepository {
    override fun monthlyTotals(
        workspaceId: WorkspaceId,
        categoryIds: List<CategoryId>,
        type: TransactionType,
        baseCurrency: CurrencyCode?,
        fromMonth: YearMonth,
        toMonth: YearMonth,
    ): Flow<List<CategoryMonthlyTotal>> = MutableStateFlow(
        if (baseCurrency != rowCurrency) {
            emptyList()
        } else {
            totalsFor(toMonth).filter { it.categoryId in categoryIds }
        },
    )
}

/**
 * The spend history use case *observes* the workspace for its base currency, so this is backed by a
 * flow the test can write to — a workspace row arriving late, or its currency changing, has to
 * re-point a subscription that is already running.
 */
private class FakeWorkspaceRepository(private val workspaces: Flow<List<Workspace>>) : WorkspaceRepository {
    override fun getAll(): Flow<List<Workspace>> = workspaces
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = workspaces
    override suspend fun getById(id: WorkspaceId): Workspace? =
        workspaces.first().firstOrNull { it.id == id }
    override suspend fun insert(workspace: Workspace) = error("not used")
    override suspend fun update(workspace: Workspace) = error("not used")
    override suspend fun delete(id: WorkspaceId) = error("not used")
}

private class FakeCategoryRepository(
    private val flow: Flow<List<Category>>,
    private val snapshot: () -> List<Category>,
    private val write: (List<Category>) -> Unit,
) : CategoryRepository {
    override fun getAll(): Flow<List<Category>> = flow
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flow
    override suspend fun getById(id: CategoryId): Category? = snapshot().firstOrNull { it.id == id }
    override suspend fun insert(category: Category) = write(snapshot() + category)
    override suspend fun update(category: Category) =
        write(snapshot().map { if (it.id == category.id) category else it })

    override suspend fun delete(id: CategoryId) = write(snapshot().filterNot { it.id == id })
}

private class FakeTransactionRepository(
    transactions: List<Transaction>,
) : TransactionRepository {
    private val flow = MutableStateFlow(transactions)

    override fun getAll(): Flow<List<Transaction>> = flow

    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        flow.map { list -> list.filter { it.accountId == accountId } }

    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = flow.map { list ->
        list.filter { accountId == null || it.accountId == accountId }
            .filter { it.operationDate in window }
            .take(limit)
            .map { CategorizedTransaction(it, categoryName = null) }
    }

    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = flowOf(emptyList())

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flow
    override suspend fun getById(id: TransactionId): Transaction? = flow.value.firstOrNull { it.id == id }
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        flow.value.filter { it.transferId == transferId }
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
        flow.value.filter { it.splitId == splitId }
    override suspend fun insert(transaction: Transaction) {
        flow.value = flow.value + transaction
    }
    override suspend fun update(transaction: Transaction) {
        flow.value = flow.value.map { if (it.id == transaction.id) transaction else it }
    }
    override suspend fun delete(id: TransactionId) {
        flow.value = flow.value.filterNot { it.id == id }
    }
}
