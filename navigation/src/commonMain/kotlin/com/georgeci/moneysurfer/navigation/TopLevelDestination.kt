package com.georgeci.moneysurfer.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import moneysurfer.navigation.generated.resources.Res
import moneysurfer.navigation.generated.resources.nav_accounts
import moneysurfer.navigation.generated.resources.nav_categories
import moneysurfer.navigation.generated.resources.nav_dashboard
import moneysurfer.navigation.generated.resources.nav_settings
import moneysurfer.navigation.generated.resources.nav_transactions
import org.jetbrains.compose.resources.StringResource

internal enum class TopLevelDestination(
    val route: Route.TopLevel,
    val icon: ImageVector,
    val label: StringResource,
) {
    Dashboard(Route.Dashboard, SurferIcons.Dashboard, Res.string.nav_dashboard),
    Transactions(
        route = Route.TransactionsByAccount(accountId = null),
        icon = SurferIcons.SwapHoriz,
        label = Res.string.nav_transactions,
    ),
    Accounts(Route.AccountsManage, SurferIcons.Wallet, Res.string.nav_accounts),
    Categories(Route.CategoriesManage, SurferIcons.Category, Res.string.nav_categories),
    Settings(Route.Settings, SurferIcons.Settings, Res.string.nav_settings),
    ;

    fun matches(current: Route.TopLevel?): Boolean = when (this) {
        Dashboard -> current is Route.Dashboard
        Transactions -> current is Route.TransactionsByAccount
        Accounts -> current is Route.AccountsManage
        Categories -> current is Route.CategoriesManage
        Settings -> current is Route.Settings
    }
}
