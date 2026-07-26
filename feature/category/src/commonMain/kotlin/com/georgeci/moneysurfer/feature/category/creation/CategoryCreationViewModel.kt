package com.georgeci.moneysurfer.feature.category.creation

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.model.CategoryCapCoverage
import com.georgeci.moneysurfer.domain.model.CategoryTree
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.usecase.CategoryCapUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.EditCategoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_creation_created_snackbar
import moneysurfer.feature.category.generated.resources.category_creation_updated_snackbar
import org.koin.core.annotation.KoinViewModel

/** Local UI model for the category type toggle. The domain `Category` model does not yet carry
 *  an income flag — this is kept as UI-only state for now. */
enum class CategoryTypeUi { Expense, Income }

@KoinViewModel
class CategoryCreationViewModel(
    private val categoryId: CategoryId?,
    private val createCategory: CreateCategoryUseCase,
    private val editCategory: EditCategoryUseCase,
    private val categoryRepository: CategoryRepository,
    private val getCategories: GetCategoriesUseCase,
    private val categoryCap: CategoryCapUseCase,
    private val session: SessionPointers,
    private val getCurrentTime: GetCurrentTimeUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<CategoryCreationState, CategoryCreationEvent, CategoryCreationEffect>(
    initialState = CategoryCreationState(),
) {

    init {
        if (categoryId != null) loadCategory(categoryId)
        observeParentOptions()
        observeCapCoverage()
    }

    override fun onEvent(event: CategoryCreationEvent) {
        when (event) {
            is CategoryCreationEvent.OnNameChanged -> updateState {
                copy(
                    name = event.name.take(CategoryCreationState.NAME_MAX_LENGTH),
                    nameTouched = true,
                )
            }
            is CategoryCreationEvent.OnTypeChanged -> updateState { withType(event.type) }
            is CategoryCreationEvent.OnIconSelected -> updateState { copy(iconKey = event.iconKey) }
            is CategoryCreationEvent.OnColorSelected -> updateState { copy(hue = event.hue) }
            is CategoryCreationEvent.OnParentSelected -> updateState { copy(parentId = event.parentId) }
            is CategoryCreationEvent.OnCapChanged -> updateState { copy(cap = event.cap, capTouched = true) }
            CategoryCreationEvent.OnCancelClick -> postSideEffect(CategoryCreationEffect.NavigateBack)
            CategoryCreationEvent.OnBackClick -> postSideEffect(CategoryCreationEffect.NavigateBack)
            CategoryCreationEvent.OnSaveClick -> saveCategory()
        }
    }

    private fun loadCategory(id: CategoryId) {
        launch {
            updateState { copy(isLoading = true) }
            val category = categoryRepository.getById(id)
            if (category == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            updateState {
                copy(
                    name = category.name,
                    type = if (category.type == CategoryType.INCOME) CategoryTypeUi.Income else CategoryTypeUi.Expense,
                    iconKey = category.iconKey,
                    hue = category.hue,
                    parentId = category.parentId,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Keeps the parent picker's options in step with the type toggle. Recomputing on every
     * emission also handles the category itself changing under us (a sync pull re-parenting it),
     * and drops a selected parent that is no longer eligible instead of saving a stale pointer.
     */
    private fun observeParentOptions() {
        launch {
            getCategories().collect { categories ->
                updateState { withParentOptions(categories) }
            }
        }
    }

    /** Keeps the monthly-cap control pointed at the budget it speaks for. */
    private fun observeCapCoverage() {
        launch {
            categoryCap.coverageOf(categoryId).collect { coverage ->
                updateState { withCapCoverage(coverage) }
            }
        }
    }

    private fun CategoryCreationState.withCapCoverage(coverage: CategoryCapCoverage): CategoryCreationState =
        when (coverage) {
            is CategoryCapCoverage.Editable -> copy(
                capManagedByBudgetName = null,
                // Seed the field from storage, but never overwrite what the user is typing.
                cap = if (capTouched) cap else coverage.budget?.amount?.toAmountInput().orEmpty(),
            )
            // Read-only: the amount belongs to a budget spanning more than this category, so
            // showing it in the field would invite the user to edit someone else's limit.
            is CategoryCapCoverage.Managed -> copy(capManagedByBudgetName = coverage.budget.name)
        }

    private fun CategoryCreationState.withType(type: CategoryTypeUi): CategoryCreationState =
        copy(type = type).withParentOptions(allCategories)

    private fun CategoryCreationState.withParentOptions(categories: List<Category>): CategoryCreationState {
        val eligible = CategoryTree.eligibleParents(
            categories = categories,
            categoryId = categoryId,
            type = type.toDomain(),
        )
        return copy(
            allCategories = categories,
            parentOptions = eligible.map { CategoryParentOption(it.id, it.name) },
            parentId = parentId?.takeIf { selected -> eligible.any { it.id == selected } },
        )
    }

    private fun saveCategory() {
        val state = currentState
        if (state.name.isBlank()) return
        launch {
            updateState { copy(isLoading = true) }

            val trimmedName = state.name.trim()
            if (categoryId != null) {
                val existing = categoryRepository.getById(categoryId)
                if (existing != null) {
                    val updated = existing.copy(
                        name = trimmedName,
                        type = state.type.toDomain(),
                        iconKey = state.iconKey,
                        hue = state.hue,
                        parentId = state.parentId,
                    )
                    editCategory(updated)
                    categoryCap(category = updated, previousName = existing.name, cap = state.capAsMoney)
                    snackbar.show(Res.string.category_creation_updated_snackbar, listOf(trimmedName))
                }
                postSideEffect(CategoryCreationEffect.NavigateBack)
                return@launch
            }

            val workspaceId = session.currentWorkspaceId.first()
            if (workspaceId == null) {
                updateState { copy(isLoading = false) }
                return@launch
            }
            val created = Category(
                id = CategoryId.uuid(),
                workspaceId = workspaceId,
                name = trimmedName,
                type = state.type.toDomain(),
                parentId = state.parentId,
                createdAt = getCurrentTime(),
                iconKey = state.iconKey,
                hue = state.hue,
            )
            createCategory(created)
            categoryCap(category = created, previousName = null, cap = state.capAsMoney)
            snackbar.show(Res.string.category_creation_created_snackbar, listOf(trimmedName))
            postSideEffect(CategoryCreationEffect.NavigateBack)
        }
    }
}

private fun CategoryTypeUi.toDomain(): CategoryType =
    if (this == CategoryTypeUi.Income) CategoryType.INCOME else CategoryType.EXPENSE

private const val MINOR_PER_MAJOR = 100
private const val DECIMALS = 2

/** Money → the text the cap field shows, in major units with two decimals. */
private fun Money.toAmountInput(): String {
    val major = minor / MINOR_PER_MAJOR
    val cents = (minor % MINOR_PER_MAJOR).toInt()
    return if (cents == 0) major.toString() else "$major.${cents.toString().padStart(DECIMALS, '0')}"
}

/** One row of the parent picker. */
data class CategoryParentOption(
    val id: CategoryId,
    val name: String,
)

data class CategoryCreationState(
    val name: String = "",
    val type: CategoryTypeUi = CategoryTypeUi.Expense,
    /** Stored semantic icon key, not a grid index — see `CategoryAppearance`. */
    val iconKey: String = CategoryAppearance.ICON_KEYS.first(),
    /** Stored hue in degrees, not a swatch index. */
    val hue: Int = CategoryAppearance.HUES.first(),
    val parentId: CategoryId? = null,
    val parentOptions: List<CategoryParentOption> = emptyList(),
    /** Raw monthly-cap field text in major units, empty for "no cap". Backed by a budget, not by
     *  a field on `Category` — see `CategoryCapCoverage`. */
    val cap: String = "",
    /** Name of the budget that already limits this category alongside others. Non-null puts the
     *  cap control in its read-only state so the shortcut cannot add a second overlapping limit. */
    val capManagedByBudgetName: String? = null,
    val isLoading: Boolean = false,
    /** Whether the user has edited the name field — gates the required-name inline error
     *  so a pristine screen doesn't open covered in red. */
    val nameTouched: Boolean = false,
    /** Whether the user has edited the cap field — stops a later budget emission from overwriting
     *  what they are typing. */
    internal val capTouched: Boolean = false,
    /** Last workspace snapshot, kept so the type toggle can re-filter parents without re-querying. */
    internal val allCategories: List<Category> = emptyList(),
) {
    val canSave: Boolean get() = name.isNotBlank() && !isLoading

    /** Whether to show the required-name inline error: the field was edited and left blank. */
    val nameMissing: Boolean get() = nameTouched && name.isBlank()

    /** Whether the name is long enough that the character counter should appear. */
    val showNameCounter: Boolean get() = name.length >= NAME_COUNTER_THRESHOLD

    /** A budget caps spending, so the control is offered for expense categories only. */
    val showCap: Boolean get() = type == CategoryTypeUi.Expense

    /** The typed cap as money, or null when the field is empty, unparsable or not positive. */
    val capAsMoney: Money?
        get() = cap.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let(Money::fromDouble)

    companion object {
        /** Hard cap on the category name — longer input is truncated in the reducer. */
        const val NAME_MAX_LENGTH = 50

        /** Name length at which the "n/max" character counter becomes visible. */
        const val NAME_COUNTER_THRESHOLD = 40
    }
}

sealed interface CategoryCreationEvent {
    data class OnNameChanged(val name: String) : CategoryCreationEvent
    data class OnTypeChanged(val type: CategoryTypeUi) : CategoryCreationEvent
    data class OnIconSelected(val iconKey: String) : CategoryCreationEvent
    data class OnColorSelected(val hue: Int) : CategoryCreationEvent

    /** Null clears the parent, putting the category back at the root. */
    data class OnParentSelected(val parentId: CategoryId?) : CategoryCreationEvent

    /** Raw cap field text. Empty means "no cap" and clears the backing budget on save. */
    data class OnCapChanged(val cap: String) : CategoryCreationEvent
    data object OnCancelClick : CategoryCreationEvent
    data object OnBackClick : CategoryCreationEvent
    data object OnSaveClick : CategoryCreationEvent
}

sealed interface CategoryCreationEffect {
    data object NavigateBack : CategoryCreationEffect
}
