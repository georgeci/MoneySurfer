package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

fun anAccount(
    id: AccountId = accountId(),
    workspaceId: WorkspaceId = workspaceId(),
    name: String = "Cash",
    type: AccountType = AccountType.CASH,
    currencyCode: CurrencyCode = USD,
    balance: Money = Money.zero(),
    archived: Boolean = false,
    archivedAt: Instant? = null,
): Account = Account(
    id = id,
    workspaceId = workspaceId,
    name = name,
    type = type,
    currencyCode = currencyCode,
    balance = balance,
    archived = archived,
    archivedAt = archivedAt,
)
