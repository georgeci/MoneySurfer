package com.georgeci.moneysurfer.domain.model

data class CategorizedTransaction(
    val transaction: Transaction,
    val categoryName: String?,
)
