package com.georgeci.moneysurfer.feature.category.details

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryMonthlyTotal
import com.georgeci.moneysurfer.domain.model.CategorySpendHistory
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.CategorySpendRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendHistoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByCategoryUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
) {
    private val workspace = workspaceId("ws-1")

    // Replay-1 and deliberately unseeded when no categories are given, so the "still loading"
    // case is reachable: a StateFlow always has a value, which would skip Loading entirely.
    private val categoryFlow = MutableSharedFlow<List<Category>>(replay = 1)
    private var current: List<Category> = categories.orEmpty()

    init {
        if (categories != null) check(categoryFlow.tryEmit(categories)) { "failed to seed categories" }
    }

    fun setCategories(next: List<Category>) {
        current = next
        check(categoryFlow.tryEmit(next)) { "failed to emit categories" }
    }

    fun newViewModel(categoryId: CategoryId): CategoryDetailsViewModel {
        val session = InMemorySessionPointers(currentWorkspaceId = workspace)
        val categoryRepo = FakeCategoryRepository(
            flow = categoryFlow,
            snapshot = { current },
            write = ::setCategories,
        )
        val transactionRepo = FakeTransactionRepository(transactions)
        val spendRepo = FakeCategorySpendRepository(totalsFor)
        return CategoryDetailsViewModel(
            categoryId = categoryId,
            getCategories = GetCategoriesUseCase(categoryRepo, session),
            getCategorySpendHistory = GetCategorySpendHistoryUseCase(
                categoryRepository = categoryRepo,
                spendRepository = spendRepo,
                session = session,
                clock = ClockUseCase(),
            ),
            getTransactionsByCategory = GetTransactionsByCategoryUseCase(
                transactionRepository = transactionRepo,
                categoryRepository = categoryRepo,
                session = session,
            ),
        )
    }
}

private class FakeCategorySpendRepository(
    private val totalsFor: (YearMonth) -> List<CategoryMonthlyTotal>,
) : CategorySpendRepository {
    override fun monthlyTotals(
        workspaceId: WorkspaceId,
        categoryIds: List<CategoryId>,
        type: TransactionType,
        fromMonth: YearMonth,
        toMonth: YearMonth,
    ): Flow<List<CategoryMonthlyTotal>> =
        MutableStateFlow(totalsFor(toMonth).filter { it.categoryId in categoryIds })
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
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> =
        flow.map { list -> list.map { CategorizedTransaction(it, categoryName = null) } }

    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        flow.map { list -> list.filter { it.accountId == accountId } }

    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        flow.map { list ->
            list.filter { it.accountId == accountId }
                .map { CategorizedTransaction(it, categoryName = null) }
        }

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flow
    override suspend fun getById(id: TransactionId): Transaction? = flow.value.firstOrNull { it.id == id }
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
