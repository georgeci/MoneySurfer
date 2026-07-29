package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

/** Sample note shared by the `@Preview` states so they read as the same transaction. */
internal const val PreviewNote = "Lidl — weekly shop"
private const val PreviewWorkspaceId = "preview-ws-1"

internal val PreviewAccounts = listOf(
    Account(
        id = AccountId("preview-acc-1"),
        workspaceId = WorkspaceId(PreviewWorkspaceId),
        name = "Everyday",
        type = AccountType.CARD,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.fromMajor(2480),
    ),
    Account(
        id = AccountId("preview-acc-2"),
        workspaceId = WorkspaceId(PreviewWorkspaceId),
        name = "Savings",
        type = AccountType.SAVINGS,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.fromMajor(8300),
    ),
)

internal val PreviewCategories = listOf(
    "Groceries",
    "Transport",
    "Dining",
    "Home",
    "Leisure",
    "Health",
    "Utilities",
).mapIndexed { index, name ->
    Category(
        CategoryId("preview-cat-${index + 1}"),
        WorkspaceId(PreviewWorkspaceId),
        name,
        CategoryType.EXPENSE,
        null,
        Instant.fromEpochMilliseconds(0),
    )
}
