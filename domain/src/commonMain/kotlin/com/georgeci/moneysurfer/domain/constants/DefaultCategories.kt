package com.georgeci.moneysurfer.domain.constants

import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.CategoryType

data class DefaultCategorySeed(
    val name: String,
    val type: CategoryType,
    val systemKind: CategorySystemKind? = null,
)

val DEFAULT_CATEGORY_SEEDS: List<DefaultCategorySeed> = listOf(
    DefaultCategorySeed("Groceries", CategoryType.EXPENSE),
    DefaultCategorySeed("Restaurants", CategoryType.EXPENSE),
    DefaultCategorySeed("Transport", CategoryType.EXPENSE),
    DefaultCategorySeed("Rent", CategoryType.EXPENSE),
    DefaultCategorySeed("Utilities", CategoryType.EXPENSE),
    DefaultCategorySeed("Entertainment", CategoryType.EXPENSE),
    DefaultCategorySeed("Shopping", CategoryType.EXPENSE),
    DefaultCategorySeed("Health", CategoryType.EXPENSE),
    DefaultCategorySeed("Subscriptions", CategoryType.EXPENSE),
    DefaultCategorySeed("Travel", CategoryType.EXPENSE),
    DefaultCategorySeed("Salary", CategoryType.INCOME),
    DefaultCategorySeed("Bonus", CategoryType.INCOME),
    DefaultCategorySeed("Refunds", CategoryType.INCOME),
    DefaultCategorySeed("Transfer", CategoryType.TRANSFER, CategorySystemKind.TRANSFER),
)
