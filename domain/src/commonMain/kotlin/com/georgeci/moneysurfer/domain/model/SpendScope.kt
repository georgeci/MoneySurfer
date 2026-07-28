package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow

/**
 * Everything every spend aggregate is scoped by: which workspace, which currency counts, and
 * which slice of the calendar.
 *
 * One parameter object rather than three repeated arguments so a caller cannot pass the window of
 * one screen with the workspace of another, and so adding a filter later (account, category)
 * touches one type instead of five signatures.
 *
 * [baseCurrency] is the workspace base currency, and it is a filter rather than a formatting hint
 * — see [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository] for why v1
 * converts nothing.
 */
data class SpendScope(
    val workspaceId: WorkspaceId,
    val baseCurrency: CurrencyCode,
    val window: TransactionPeriodWindow,
)
