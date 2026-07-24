package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

// Every parameter is defaulted — callers name only the field under test, as in the Budget and
// Goal fixtures next door.
@Suppress("LongParameterList")
fun anAccount(
    id: AccountId = accountId(),
    workspaceId: WorkspaceId = workspaceId(),
    name: String = "Cash",
    type: AccountType = AccountType.CASH,
    currencyCode: CurrencyCode = USD,
    balance: Money = Money.zero(),
    archived: Boolean = false,
    extraDetails: List<AccountExtraDetail> = emptyList(),
): Account = Account(
    id = id,
    workspaceId = workspaceId,
    name = name,
    type = type,
    currencyCode = currencyCode,
    balance = balance,
    archived = archived,
    extraDetails = extraDetails,
)
