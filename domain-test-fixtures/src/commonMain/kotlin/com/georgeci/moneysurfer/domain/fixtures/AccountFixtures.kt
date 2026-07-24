package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

// Every parameter is defaulted — callers name only the field under test. Deliberately not
// annotated `@Suppress("LongParameterList")` like the Budget and Goal fixtures next door: detekt
// already excludes `**/*-test-fixtures/**` from that rule, and Sonar's own S107 does not answer to
// that name (see BudgetFixtures, which carries the annotation and is flagged anyway).
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
