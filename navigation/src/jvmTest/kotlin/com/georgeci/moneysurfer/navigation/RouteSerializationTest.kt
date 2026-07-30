package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavKey
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

/**
 * Every route that can land on the saved back stack, round-tripped through the polymorphic module
 * the saved-state configuration actually uses.
 *
 * `RouteSerializerRegistryTest` proves each route is *registered*; this proves each one survives
 * being written and read back with its arguments intact. Restoration after process death is the
 * only place this runs in production, which is why it is worth pinning: a route whose argument
 * silently comes back as its default sends the user to the wrong screen, and only on the path
 * nobody exercises by hand.
 */
class RouteSerializationTest : FunSpec({

    val json = Json { serializersModule = navKeySerializersModule }
    val navKey = PolymorphicSerializer(NavKey::class)

    fun roundTrip(route: Route): NavKey =
        json.decodeFromString(navKey, json.encodeToString(navKey, route))

    context("a route survives the saved back stack with its arguments") {
        withData(
            nameFn = { it::class.simpleName ?: "route" },
            Route.Onboarding,
            Route.SignIn,
            Route.Legal,
            Route.WorkspaceSelector(showActions = true, cloudDataUnavailable = true),
            Route.WorkspaceCreation(workspaceId = "ws-1"),
            Route.WorkspaceMembers(workspaceId = "ws-1"),
            Route.WorkspaceManage(workspaceId = "ws-1"),
            Route.WorkspaceInvite(workspaceId = "ws-1"),
            Route.WorkspaceMemberActions(workspaceId = "ws-1", targetUserId = "u-2"),
            Route.IncomingInvites,
            Route.Dashboard,
            Route.DashboardCustomize,
            Route.AccountCreation(accountId = "a-1", firstRun = true, accountType = "BANK"),
            Route.AccountsManage,
            Route.CategoryCreation(categoryId = "c-1"),
            Route.CategoriesManage,
            Route.CategoryDetails(categoryId = "c-1"),
            Route.CategoryChooser(
                selectedCategoryId = "c-1",
                filterType = "EXPENSE",
                variant = "GRID",
            ),
            Route.Budgets,
            Route.BudgetDetails(budgetId = "b-1"),
            Route.BudgetCreation(budgetId = "b-1"),
            Route.AccountChooser(
                selectedAccountId = "a-1",
                excludeAccountId = "a-2",
                showTransferShortcut = true,
            ),
            Route.TransactionsByAccount(accountId = "a-1"),
            Route.AccountTransactionCreation(accountId = "a-1"),
            Route.TransactionCreation(
                transactionId = "t-1",
                accountId = "a-1",
                duplicate = true,
                transfer = true,
            ),
            Route.TransactionFilters(accountId = "a-1", anchorEpochDay = 20_000L),
            Route.AccountDetails(accountId = "a-1"),
            Route.TransactionDetails(transactionId = "t-1"),
            Route.Goals,
            Route.GoalDetails(goalId = "g-1"),
            Route.GoalCreation,
            Route.GoalEdit(goalId = "g-1"),
            Route.GoalContribution(goalId = "g-1", mode = GoalContributionMode.WITHDRAW.name),
            Route.Settings,
            Route.SettingsAppearance,
            Route.SettingsPreferences,
            Route.SettingsSync,
            Route.SettingsBackup,
            Route.SettingsCsv,
            Route.SettingsAbout,
            Route.SettingsLicenses,
            Route.SettingsDeleteAccount,
            Route.SettingsDebugConfig,
            Route.SettingsDebugLog,
        ) { route ->
            roundTrip(route) shouldBe route
        }
    }

    // Defaults are what a caller relies on when it pushes a route with no arguments; a restored
    // stack that filled them in differently would change behaviour only after process death.
    test("a route pushed with no arguments restores with its defaults") {
        roundTrip(Route.TransactionCreation()) shouldBe Route.TransactionCreation(
            transactionId = null,
            accountId = null,
            duplicate = false,
            transfer = false,
        )
    }

    test("a goal contribution defaults to adding money") {
        roundTrip(Route.GoalContribution(goalId = "g-1")) shouldBe
            Route.GoalContribution(goalId = "g-1", mode = GoalContributionMode.ADD.name)
    }
})
