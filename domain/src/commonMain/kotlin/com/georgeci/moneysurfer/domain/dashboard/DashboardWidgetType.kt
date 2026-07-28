package com.georgeci.moneysurfer.domain.dashboard

/**
 * Registry of the widgets the dashboard can render. The dashboard screen has a renderer for every
 * entry here, so adding a widget is a two-step change: add the constant, then handle it in the
 * screen's `when`. Entries are also the persistence keys of a saved layout — renaming one drops
 * that widget from layouts already stored on device, so prefer adding over renaming.
 */
enum class DashboardWidgetType {
    Balance,
    QuickActions,
    SafeToSpend,
    BurnRate,
    Accounts,
    Goals,
    RecentTransactions,
}
