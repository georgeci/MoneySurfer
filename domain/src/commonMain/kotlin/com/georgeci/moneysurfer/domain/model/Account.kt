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
    /**
     * When the account was archived, or `null` while it is active. Set by the repository on
     * archive and cleared on restore — the manage list renders it as "Archived Nov 2024", which
     * `updatedAt` cannot supply because balance recalculation keeps bumping it.
     */
    val archivedAt: Instant? = null,
    /** Optional user-entered key–value details (IBAN, description, custom fields, …). */
    val extraDetails: List<AccountExtraDetail> = emptyList(),
)
