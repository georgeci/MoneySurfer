package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

data class Account(
    val id: AccountId,
    val workspaceId: WorkspaceId,
    val name: String,
    val type: AccountType,
    val currencyCode: CurrencyCode,
    val balance: Money,
    val archived: Boolean = false,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
)
