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

internal val PreviewAccounts = listOf(
    Account(
        id = AccountId("preview-acc-1"),
        workspaceId = WorkspaceId("preview-ws-1"),
        name = "Everyday",
        type = AccountType.CARD,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.fromMajor(2480),
    ),
    Account(
        id = AccountId("preview-acc-2"),
        workspaceId = WorkspaceId("preview-ws-1"),
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
        WorkspaceId("preview-ws-1"),
        name,
        CategoryType.EXPENSE,
        null,
        Instant.fromEpochMilliseconds(0),
    )
}
