package com.georgeci.moneysurfer.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface Route : NavKey {

    @Serializable
    data object SignIn : Route

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
    data object Dashboard : Route

    @Serializable
    data class AccountCreation(
        val accountId: String? = null,
    ) : Route

    @Serializable
    data object AccountsManage : Route

    @Serializable
    data class CategoryCreation(
        val categoryId: String? = null,
    ) : Route

    @Serializable
    data object CategoriesManage : Route

    @Serializable
    data class CategoryChooser(
        val selectedCategoryId: String? = null,
        val filterType: String? = null,
    ) : Route

    @Serializable
    data class AccountChooser(
        val selectedAccountId: String? = null,
    ) : Route

    @Serializable
    data class TransactionsByAccount(val accountId: String? = null) : Route

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
    data object Settings : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsPreferences : Route

    @Serializable
    data object SettingsSync : Route

    @Serializable
    data object SettingsBackup : Route

    @Serializable
    data object SettingsAbout : Route
}
