package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class GetCurrenciesUseCase(
    private val currencyRepository: CurrencyRepository,
) {
    operator fun invoke(): Flow<List<Currency>> = currencyRepository.getAll()
}
