package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

data class Transaction(
    val id: TransactionId,
    val workspaceId: WorkspaceId,
    val accountId: AccountId,
    val money: Money,
    val currencyCode: CurrencyCode,
    val categoryId: CategoryId?,
    val note: String,
    val operationAt: Instant,
    val type: TransactionType,
    val status: TransactionStatus = TransactionStatus.ACTUAL,
    val createdAt: Instant = operationAt,
    val updatedAt: Instant = createdAt,
)
