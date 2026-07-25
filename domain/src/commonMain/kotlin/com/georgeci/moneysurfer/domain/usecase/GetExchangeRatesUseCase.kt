package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.ExchangeRateSnapshot
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Rates for whatever base currency the active workspace is on, kept current.
 *
 * The refresh is launched *beside* the cache stream rather than before it: collectors get the
 * cached table (possibly `null`) on the first frame and the refreshed one whenever it lands, so
 * a slow or dead network delays nothing on screen. Switching workspace — or changing the
 * workspace currency — re-points both halves at the new base.
 *
 * Emits `null` while no workspace is selected.
 */
@Single
@OptIn(ExperimentalCoroutinesApi::class)
class GetExchangeRatesUseCase(
    private val session: SessionPointers,
    private val workspaceRepository: WorkspaceRepository,
    private val exchangeRates: ExchangeRateRepository,
) {

    operator fun invoke(): Flow<ExchangeRateSnapshot?> =
        baseCurrency()
            .distinctUntilChanged()
            .flatMapLatest { base ->
                if (base == null) flowOf(null) else ratesFor(base)
            }

    private fun baseCurrency(): Flow<CurrencyCode?> =
        session.currentWorkspaceId.flatMapLatest { workspaceId ->
            if (workspaceId == null) {
                flowOf(null)
            } else {
                workspaceRepository.getAll()
                    .map { workspaces -> workspaces.firstOrNull { it.id == workspaceId }?.baseCurrency }
            }
        }

    private fun ratesFor(base: CurrencyCode): Flow<ExchangeRateSnapshot> = channelFlow {
        launch { exchangeRates.refreshIfStale(base) }
        exchangeRates.observe(base).collect { table -> send(ExchangeRateSnapshot(base, table)) }
    }
}
