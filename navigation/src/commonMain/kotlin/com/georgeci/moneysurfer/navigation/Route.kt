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

    @Serializable
    data class WorkspaceSelector(
        val showActions: Boolean = false,
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

    @Serializable
    data class CategoryChooser(
        val selectedCategoryId: String? = null,
        val filterType: String? = null,
    ) : Route

    @Serializable
    data class AccountChooser(
        val selectedAccountId: String? = null,
        val excludeAccountId: String? = null,
    ) : Route

    @Serializable
    data class TransactionsByAccount(val accountId: String? = null) : TopLevel

    @Serializable
    data class TransactionCreation(
        val transactionId: String? = null,
        val accountId: String? = null,
    ) : Route

    @Serializable
    data class AccountDetails(val accountId: String) : Route

    @Serializable
    data class TransactionDetails(val transactionId: String) : Route

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
}
