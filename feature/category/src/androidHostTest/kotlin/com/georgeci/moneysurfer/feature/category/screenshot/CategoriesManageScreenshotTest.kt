package com.georgeci.moneysurfer.feature.category.screenshot

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.feature.category.manage.CategoriesManageContent
import com.georgeci.moneysurfer.feature.category.manage.CategoriesManagePendingDelete
import com.georgeci.moneysurfer.feature.category.manage.CategoriesManageState
import com.georgeci.moneysurfer.feature.category.manage.CategoryManageUi
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreen
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Full-screen captures of the category tree (issue #85).
 *
 * The list is handed an already-flattened tree, so the indent is the only thing that says a row is
 * a child — which is exactly the kind of detail a diff of PNGs catches and an assertion on the
 * state does not. The rows are captured at rest: `SurferSwipeRevealRow` keeps its edit and delete
 * actions off-screen until a row is dragged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class CategoriesManageScreenshotTest {

    /** The shape a used workspace has: nested expenses, a system category, income at the bottom. */
    @Test
    fun categoriesManage() = captureFullScreen("categories_manage") {
        CategoriesManageContent(
            state = CategoriesManageState.Content(categories = NestedTree),
            onEvent = {},
        )
    }

    /** The seeded set before anyone nests anything — roots only, so no row is indented. */
    @Test
    fun categoriesManageFlat() = captureFullScreen("categories_manage_flat") {
        CategoriesManageContent(
            state = CategoriesManageState.Content(
                categories = NestedTree.filter { it.parentId == null },
            ),
            onEvent = {},
        )
    }

    @Test
    fun categoriesManageEmpty() = captureFullScreen("categories_manage_empty") {
        CategoriesManageContent(
            state = CategoriesManageState.Content(categories = emptyList()),
            onEvent = {},
        )
    }

    /** Deleting a parent: the dialog has to say where the children end up. */
    @Test
    fun categoriesManageDeleteDialog() = captureFullScreen("categories_manage_delete_dialog") {
        CategoriesManageContent(
            state = CategoriesManageState.Content(
                categories = NestedTree,
                pendingDelete = CategoriesManagePendingDelete(
                    id = CategoryId("screenshot-cat-food"),
                    name = "Food",
                    childCount = 2,
                ),
            ),
            onEvent = {},
        )
    }

    private companion object {

        val NestedTree = listOf(
            expense("screenshot-cat-food", "Food", slot = 0),
            expense("screenshot-cat-groceries", "Groceries", slot = 1, parent = "screenshot-cat-food"),
            expense("screenshot-cat-dining", "Dining", slot = 5, parent = "screenshot-cat-food"),
            expense("screenshot-cat-transport", "Transport", slot = 2),
            expense("screenshot-cat-home", "Home", slot = 3),
            CategoryManageUi(
                id = CategoryId("screenshot-cat-transfer"),
                name = "Transfer",
                type = CategoryType.EXPENSE,
                iconKey = SurferCategoryPalette.TRANSFER_ICON_KEY,
                systemKind = SurferCategoryPalette.SYSTEM_KIND_TRANSFER,
            ),
            CategoryManageUi(
                id = CategoryId("screenshot-cat-salary"),
                name = "Salary",
                type = CategoryType.INCOME,
                iconKey = SurferCategoryPalette.iconKeys[6],
                hue = SurferCategoryPalette.hues[6],
            ),
            CategoryManageUi(
                id = CategoryId("screenshot-cat-bonus"),
                name = "Bonus",
                type = CategoryType.INCOME,
                parentId = CategoryId("screenshot-cat-salary"),
                iconKey = SurferCategoryPalette.iconKeys[7],
                hue = SurferCategoryPalette.hues[7],
                depth = 1,
            ),
        )

        /**
         * A row filed under [parent] when given one, which is also what puts it at depth 1 — the
         * list is already in tree order, so those two are all the nesting the screen reads.
         */
        fun expense(id: String, name: String, slot: Int, parent: String? = null) = CategoryManageUi(
            id = CategoryId(id),
            name = name,
            type = CategoryType.EXPENSE,
            parentId = parent?.let(::CategoryId),
            iconKey = SurferCategoryPalette.iconKeys[slot],
            hue = SurferCategoryPalette.hues[slot],
            depth = if (parent == null) 0 else 1,
        )
    }
}
