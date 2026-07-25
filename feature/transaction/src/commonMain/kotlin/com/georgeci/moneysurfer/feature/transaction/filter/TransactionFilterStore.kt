package com.georgeci.moneysurfer.feature.transaction.filter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.Single

/**
 * The applied filters, held above both the transactions list and the filter screen.
 *
 * Neither ViewModel can own this: the filter screen is a separate destination, so a
 * list-owned copy would be gone by the time it commits, and a screen-owned copy would not exist
 * until the user opened it. A singleton is what "shared by two destinations, survives the
 * navigation between them" means here.
 *
 * Only *applied* filters live here. The filter screen edits its own draft and calls [commit] on
 * `Apply`; `Cancel` simply drops the draft, which is why backing out changes nothing.
 *
 * Scoped to the process, not to a screen: reopening the list keeps whatever the user last
 * applied, the same way the type chips already did within a session. Nothing is persisted across
 * launches — a filter the user cannot see the origin of is worse than re-picking it.
 */
@Single
class TransactionFilterStore {

    private val state = MutableStateFlow(TransactionFilters.Empty)

    val filters: StateFlow<TransactionFilters> = state.asStateFlow()

    /** Live as the user types on the list — the search field is not part of the draft/apply cycle. */
    fun setQuery(query: String) {
        state.update { it.copy(query = query) }
    }

    /** `Apply` on the filter screen. The draft carries the search text through unchanged. */
    fun commit(filters: TransactionFilters) {
        state.value = filters
    }

    /** Drops every filter but keeps the search text, which the list still shows. */
    fun clear() {
        state.update { it.cleared() }
    }
}
