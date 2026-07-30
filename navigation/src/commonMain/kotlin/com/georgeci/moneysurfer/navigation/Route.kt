package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {

    /** Marker for routes that appear as top-level destinations in the navigation suite (rail/drawer/bar). */
    sealed interface TopLevel : Route

    /** First-launch welcome screen, shown once before sign-in / first-account setup. */
    @Serializable
    data object Onboarding : Route

    @Serializable
    data object SignIn : Route

    @Serializable
    data object Legal : Route

    /**
     * [cloudDataUnavailable] marks the sign-in path where the account is known to own workspaces
     * that never reached the local database — the selector says so instead of looking like a
     * brand-new account with nothing in it (issue #342).
     */
    @Serializable
    data class WorkspaceSelector(
        val showActions: Boolean = false,
        val cloudDataUnavailable: Boolean = false,
    ) : Route

    @Serializable
    data class WorkspaceCreation(
        val workspaceId: String? = null,
    ) : Route

    @Serializable
    data class WorkspaceMembers(
        val workspaceId: String,
    ) : Route

    @Serializable
    data class WorkspaceManage(
        val workspaceId: String,
    ) : Route

    @Serializable
    data class WorkspaceInvite(
        val workspaceId: String,
    ) : Route

    @Serializable
    data class WorkspaceMemberActions(
        val workspaceId: String,
        val targetUserId: String,
    ) : Route

    @Serializable
    data object IncomingInvites : Route

    @Serializable
    data object Dashboard : TopLevel

    /** Widget visibility and order for the dashboard — reached from its top bar, not a tab. */
    @Serializable
    data object DashboardCustomize : Route

    /**
     * [firstRun] marks the offline first-launch step: the screen creates the very first account,
     * adopts its currency as the workspace base currency, and lands on Dashboard instead of
     * popping back. [accountType] pre-selects the type the onboarding asked about — it is the
     * `AccountType` enum name, kept as a String so `navigation` stays free of domain enums in
     * its saved back stack.
     */
    @Serializable
    data class AccountCreation(
        val accountId: String? = null,
        val firstRun: Boolean = false,
        val accountType: String? = null,
    ) : Route

    @Serializable
    data object AccountsManage : TopLevel

    @Serializable
    data class CategoryCreation(
        val categoryId: String? = null,
    ) : Route

    @Serializable
    data object CategoriesManage : TopLevel

    /**
     * Standalone spending analytics. Every other surface built on the spend aggregates is a
     * dashboard *widget*; this is the screen where the whole picture lives, so it is a destination
     * of its own rather than a detail of the dashboard.
     */
    @Serializable
    data object Insights : TopLevel

    @Serializable
    data object Budgets : TopLevel

    @Serializable
    data class BudgetDetails(val budgetId: String) : Route

    /** Create when [budgetId] is null, edit otherwise — one screen, as with accounts and categories. */
    @Serializable
    data class BudgetCreation(
        val budgetId: String? = null,
    ) : Route

    @Serializable
    data class CategoryDetails(val categoryId: String) : Route

    @Serializable
    data class CategoryChooser(
        val selectedCategoryId: String? = null,
        val filterType: String? = null,
        /**
         * Name of a `CategoryPickerVariant` — which of the two picker layouts to open. Null
         * takes the feature's default rather than binding navigation to the enum.
         */
        val variant: String? = null,
    ) : Route

    @Serializable
    data class AccountChooser(
        val selectedAccountId: String? = null,
        val excludeAccountId: String? = null,
        /** Offer the "Transfer between accounts instead" footer — only for a single-account pick. */
        val showTransferShortcut: Boolean = false,
    ) : Route

    @Serializable
    data class TransactionsByAccount(val accountId: String? = null) : TopLevel

    /**
     * Full-screen transaction filters. [accountId] is the list's own scope, not a filter: when it
     * is set the screen hides its account picker, because the list is already restricted to that
     * account. [anchorEpochDay] is the day the list is currently paged to, so the screen's live
     * result count resolves the same date window when the range still follows the period pager.
     */
    @Serializable
    data class TransactionFilters(
        val accountId: String? = null,
        val anchorEpochDay: Long? = null,
    ) : Route

    @Serializable
    data class TransactionCreation(
        val transactionId: String? = null,
        val accountId: String? = null,
        /**
         * Read [transactionId] as a template for a brand-new transaction instead of editing it.
         * A flag rather than a second id field: the screen loads exactly one transaction either
         * way, and two nullable id fields could disagree about which.
         */
        val duplicate: Boolean = false,
        /**
         * Open the form already switched to Transfer. Same request the account picker's "transfer
         * instead" footer makes once the screen is open — a caller that already knows the user
         * wants a transfer (the dashboard's quick actions) says so up front instead. The screen
         * still runs it through the type switch, so a build with transfers off ignores it.
         */
        val transfer: Boolean = false,
    ) : Route

    @Serializable
    data class AccountDetails(val accountId: String) : Route

    @Serializable
    data class TransactionDetails(val transactionId: String) : Route

    /** Savings goals list. Also reachable from the dashboard's goals widget. */
    @Serializable
    data object Goals : TopLevel

    @Serializable
    data class GoalDetails(val goalId: String) : Route

    @Serializable
    data object GoalCreation : Route

    @Serializable
    data class GoalEdit(val goalId: String) : Route

    /** [mode] is a [GoalContributionMode] name — add money or take it back out. */
    @Serializable
    data class GoalContribution(
        val goalId: String,
        val mode: String = GoalContributionMode.ADD.name,
    ) : Route

    @Serializable
    data object Settings : TopLevel

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsPreferences : Route

    @Serializable
    data object SettingsSync : Route

    @Serializable
    data object SettingsBackup : Route

    @Serializable
    data object SettingsCsv : Route

    @Serializable
    data object SettingsAbout : Route

    @Serializable
    data object SettingsLicenses : Route

    /** Online-only user-account deletion flow (issue #213). */
    @Serializable
    data object SettingsDeleteAccount : Route

    /**
     * QA configuration panel. Registered in both builds, but Settings only surfaces a way here when
     * a real debug-overrides layer is bound — release builds resolve `DebugConfigSource.Empty`.
     */
    @Serializable
    data object SettingsDebugConfig : Route

    /**
     * The last Warn/Error log lines this process produced, reachable from [SettingsDebugConfig].
     * Gated by the same debug-layer signal — the buffer behind it is only filled in debug builds.
     */
    @Serializable
    data object SettingsDebugLog : Route
}
