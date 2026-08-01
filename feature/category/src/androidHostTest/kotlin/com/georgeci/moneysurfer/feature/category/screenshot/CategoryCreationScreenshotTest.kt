package com.georgeci.moneysurfer.feature.category.screenshot

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.feature.category.creation.CategoryCreationContent
import com.georgeci.moneysurfer.feature.category.creation.CategoryCreationState
import com.georgeci.moneysurfer.feature.category.creation.CategoryParentOption
import com.georgeci.moneysurfer.feature.category.creation.CategoryTypeUi
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
 * Full-screen captures of the category editor (issue #85).
 *
 * The preview card at the top resolves its bubble through the same `SurferCategoryPalette` the
 * saved category will use, so these frames are also the reference for "what the icon and hue
 * pickers actually produce".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class CategoryCreationScreenshotTest {

    @Test
    fun categoryCreation() = captureFullScreen("category_creation") {
        CategoryCreationContent(state = filledState(), isEditing = false, onEvent = {})
    }

    /** A fresh editor: default icon and hue, no parent, no cap. */
    @Test
    fun categoryCreationEmpty() = captureFullScreen("category_creation_empty") {
        CategoryCreationContent(
            state = CategoryCreationState(parentOptions = ParentOptions),
            isEditing = false,
            onEvent = {},
        )
    }

    /** A cap limits spending, so income drops the control entirely rather than disabling it. */
    @Test
    fun categoryCreationIncome() = captureFullScreen("category_creation_income") {
        CategoryCreationContent(
            state = filledState().copy(
                name = "Bonus",
                type = CategoryTypeUi.Income,
                parentId = null,
                cap = "",
            ),
            isEditing = false,
            onEvent = {},
        )
    }

    /** A budget already covers this category: the cap goes read-only so no second limit is added. */
    @Test
    fun categoryCreationCapManagedByBudget() =
        captureFullScreen("category_creation_cap_managed_by_budget") {
            CategoryCreationContent(
                state = filledState().copy(
                    cap = "",
                    capManagedByBudgetName = "Everyday spending",
                ),
                isEditing = false,
                onEvent = {},
            )
        }

    /** Editing an existing category — same body, "Edit category" chrome. */
    @Test
    fun categoryCreationEdit() = captureFullScreen("category_creation_edit") {
        CategoryCreationContent(state = filledState(), isEditing = true, onEvent = {})
    }

    private fun filledState() = CategoryCreationState(
        name = "Coffee",
        iconKey = SurferCategoryPalette.iconKeys[1],
        hue = SurferCategoryPalette.hues[1],
        parentOptions = ParentOptions,
        parentId = CategoryId("screenshot-cat-dining"),
        cap = "250",
    )

    private companion object {
        val ParentOptions = listOf(
            CategoryParentOption(CategoryId("screenshot-cat-dining"), "Dining"),
            CategoryParentOption(CategoryId("screenshot-cat-home"), "Home"),
        )
    }
}
