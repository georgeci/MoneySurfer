package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

fun aTransaction(
    id: TransactionId = transactionId(),
    workspaceId: WorkspaceId = workspaceId(),
    accountId: AccountId = accountId(),
    money: Money = 100.dollars,
    currencyCode: CurrencyCode = USD,
    categoryId: CategoryId? = categoryId(),
    note: String = "",
    timestamp: Long = TEST_EPOCH_MILLIS,
    type: TransactionType = TransactionType.EXPENSE,
    status: TransactionStatus = TransactionStatus.ACTUAL,
): Transaction = Transaction(
    id = id,
    workspaceId = workspaceId,
    accountId = accountId,
    money = money,
    currencyCode = currencyCode,
    categoryId = categoryId,
    note = note,
    timestamp = timestamp,
    type = type,
    status = status,
)
