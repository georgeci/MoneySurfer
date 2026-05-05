package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * `operationAt` is the absolute moment of the operation (sortable). `operationDate` is
 * the business date the user assigned the transaction to — what the budget treats it as.
 * Storing both explicitly avoids re-deriving the date at every read site (timezone
 * boundaries can flip the day) and keeps backdating intent explicit (`operationDate`
 * stays as picked even if the user enters at midnight in a different zone).
 */
data class Transaction(
    val id: TransactionId,
    val workspaceId: WorkspaceId,
    val accountId: AccountId,
    val money: Money,
    val currencyCode: CurrencyCode,
    val categoryId: CategoryId?,
    val note: String,
    val operationAt: Instant,
    val operationDate: LocalDate,
    val type: TransactionType,
    val status: TransactionStatus = TransactionStatus.ACTUAL,
    // Required: technical timestamps — they intentionally differ from operationAt for
    // backdated entries. createdAt is the local-row creation moment, NOT the operation
    // moment. Aliasing them silently breaks (operationDate, operationAt, createdAt)
    // sort and audit semantics.
    val createdAt: Instant,
    val updatedAt: Instant,
)
