package com.georgeci.moneysurfer.domain.model

data class CategorizedTransaction(
    val transaction: Transaction,
    val categoryName: String?,
    /**
     * How many legs the row's split group has in storage, or `0` when the row is not a split leg.
     *
     * Counted by the window query rather than stored, and compared against the legs actually
     * present in a page: a collapsed row is only drawn when the two agree, so a group the paging
     * limit or a filter cut in half is rendered as its individual legs instead of as a total that
     * silently changes on the next "load more".
     */
    val splitLegCount: Int = 0,
)
