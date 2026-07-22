package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun aTransaction(
    id: TransactionId = transactionId(),
    workspaceId: WorkspaceId = workspaceId(),
    accountId: AccountId = accountId(),
    money: Money = 100.dollars,
    currencyCode: CurrencyCode = USD,
    categoryId: CategoryId? = categoryId(),
    note: String = "",
    merchant: String = "",
    tags: List<String> = emptyList(),
    operationAt: Instant = testInstant,
    operationDate: LocalDate = operationAt.toLocalDateTime(TimeZone.UTC).date,
    type: TransactionType = TransactionType.EXPENSE,
    status: TransactionStatus = TransactionStatus.ACTUAL,
    createdAt: Instant = operationAt,
    updatedAt: Instant = createdAt,
    transferId: TransferId? = null,
    recurringRuleId: RecurringRuleId? = null,
): Transaction = Transaction(
    id = id,
    workspaceId = workspaceId,
    accountId = accountId,
    money = money,
    currencyCode = currencyCode,
    categoryId = categoryId,
    note = note,
    merchant = merchant,
    tags = tags,
    operationAt = operationAt,
    operationDate = operationDate,
    type = type,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transferId = transferId,
    recurringRuleId = recurringRuleId,
)
