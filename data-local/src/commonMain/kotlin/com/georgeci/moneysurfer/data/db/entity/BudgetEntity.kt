package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "categoryIds") val categoryIds: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "period") val period: String,
    @ColumnInfo(name = "startDate") val startDate: String,
    @ColumnInfo(name = "alertPercent") val alertPercent: Int,
    @ColumnInfo(name = "isActive") val isActive: Boolean,
)
