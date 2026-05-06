package com.georgeci.moneysurfer.feature.account.manage

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType

internal val PreviewActiveAccountsFull = listOf(
    AccountManageUi(AccountId("preview-acc-1"), "Everyday", AccountType.BANK, "€2,480.32", "EUR"),
    AccountManageUi(AccountId("preview-acc-2"), "Emergency Fund", AccountType.SAVINGS, "€8,915.00", "EUR"),
    AccountManageUi(AccountId("preview-acc-3"), "Cash wallet", AccountType.CASH, "€180.00", "EUR"),
)

internal val PreviewActiveAccountsEditing = PreviewActiveAccountsFull.take(2)

internal val PreviewArchivedAccounts = listOf(
    AccountManageUi(
        AccountId("preview-acc-4"),
        "Revolut (old)",
        AccountType.CARD,
        "€0.00",
        "EUR",
        archivedLabel = "Archived Nov 2024",
    ),
)
