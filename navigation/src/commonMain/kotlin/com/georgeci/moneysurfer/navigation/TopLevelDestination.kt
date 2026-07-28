package com.georgeci.moneysurfer.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import moneysurfer.navigation.generated.resources.Res
import moneysurfer.navigation.generated.resources.nav_accounts
import moneysurfer.navigation.generated.resources.nav_budgets
import moneysurfer.navigation.generated.resources.nav_categories
import moneysurfer.navigation.generated.resources.nav_dashboard
import moneysurfer.navigation.generated.resources.nav_goals
import moneysurfer.navigation.generated.resources.nav_section_manage
import moneysurfer.navigation.generated.resources.nav_settings
import moneysurfer.navigation.generated.resources.nav_transactions
import org.jetbrains.compose.resources.StringResource

/**
 * The groups the navigation drawer draws its destinations in — everyday surfaces first, then the
 * things you set up once and revisit rarely.
 *
 * Only the drawer renders [label]; the rail shows one flat list, because a `NavigationRail` has
 * no room for a caption and no grouping affordance to hang one on.
 */
internal enum class NavigationSection(val label: StringResource?) {
    /** No caption — the primary group leads the drawer and needs no naming. */
    Primary(label = null),
    Manage(label = Res.string.nav_section_manage),
}

/**
 * The app's top-level destinations, in the order every width presents them.
 *
 * This list is authoritative for all three widths (issue #389): the drawer groups it by
 * [NavigationSection], the rail flattens it, and Compact deliberately presents none of it — see
 * [navigationPresentation].
 *
 * The taxonomy resolves §G7 of `md/tablet-desktop-responsive.md` towards the design's
 * `Home · Accounts · Activity · Budgets · Goals` + `Manage`, keeping the app's own names for the
 * two that differ only in wording (Dashboard, Transactions). Categories was primary and is now a
 * Manage entry — it is a setup surface, not a daily one — and the design's "Bills" is left out
 * because it has no route or screen behind it, only a dashboard widget.
 *
 * Entries must stay grouped by section, since the drawer renders each group as one contiguous
 * block in declaration order.
 */
internal enum class TopLevelDestination(
    val route: Route.TopLevel,
    val icon: ImageVector,
    val label: StringResource,
    val section: NavigationSection = NavigationSection.Primary,
) {
    Dashboard(Route.Dashboard, SurferIcons.Dashboard, Res.string.nav_dashboard),
    Accounts(Route.AccountsManage, SurferIcons.Wallet, Res.string.nav_accounts),
    Transactions(
        route = Route.TransactionsByAccount(accountId = null),
        icon = SurferIcons.SwapHoriz,
        label = Res.string.nav_transactions,
    ),
    Budgets(Route.Budgets, SurferIcons.Savings, Res.string.nav_budgets),
    Goals(Route.Goals, SurferIcons.Flag, Res.string.nav_goals),
    Categories(
        route = Route.CategoriesManage,
        icon = SurferIcons.Category,
        label = Res.string.nav_categories,
        section = NavigationSection.Manage,
    ),
    Settings(
        route = Route.Settings,
        icon = SurferIcons.Settings,
        label = Res.string.nav_settings,
        section = NavigationSection.Manage,
    ),
    ;

    fun matches(current: Route.TopLevel?): Boolean = when (this) {
        Dashboard -> current is Route.Dashboard
        Accounts -> current is Route.AccountsManage
        Transactions -> current is Route.TransactionsByAccount
        Budgets -> current is Route.Budgets
        Goals -> current is Route.Goals
        Categories -> current is Route.CategoriesManage
        Settings -> current is Route.Settings
    }
}
