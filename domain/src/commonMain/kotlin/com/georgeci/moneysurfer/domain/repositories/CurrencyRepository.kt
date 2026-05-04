package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.Currency
import kotlinx.coroutines.flow.Flow

interface CurrencyRepository {
    fun getAll(): Flow<List<Currency>>
}
