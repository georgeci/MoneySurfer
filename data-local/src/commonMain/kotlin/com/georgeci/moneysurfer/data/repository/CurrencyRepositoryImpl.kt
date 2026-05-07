package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single

/**
 * Static currency catalogue. Swap the in-memory list for a datastore/backend feed when the
 * product grows beyond this short set.
 */
@Single(binds = [CurrencyRepository::class])
class CurrencyRepositoryImpl : CurrencyRepository {

    private val currencies: List<Currency> = listOf(
        Currency(CurrencyCode("EUR"), symbol = "€", displayName = "Euro"),
        Currency(CurrencyCode("USD"), symbol = "$", displayName = "US Dollar"),
        Currency(CurrencyCode("GBP"), symbol = "£", displayName = "British Pound"),
        Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty"),
        Currency(CurrencyCode("RUB"), symbol = "₽", displayName = "Russian Ruble"),
    )

    override fun getAll(): Flow<List<Currency>> = flowOf(currencies)
}
