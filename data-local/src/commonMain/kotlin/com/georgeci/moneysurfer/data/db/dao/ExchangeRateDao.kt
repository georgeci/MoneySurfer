package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {

    @Query("SELECT * FROM exchange_rates WHERE baseCurrency = :base")
    fun observeByBase(base: String): Flow<List<ExchangeRateEntity>>

    @Query("SELECT MAX(fetchedAt) FROM exchange_rates WHERE baseCurrency = :base")
    suspend fun lastFetchedAt(base: String): Long?

    @Upsert
    suspend fun upsertAll(entities: List<ExchangeRateEntity>)

    @Query("DELETE FROM exchange_rates WHERE baseCurrency = :base")
    suspend fun deleteByBase(base: String)

    @Query("DELETE FROM exchange_rates")
    suspend fun deleteAll()

    /**
     * Swaps in a freshly fetched table for one base. Delete-then-insert rather than a plain upsert
     * so a currency the provider has stopped quoting disappears instead of lingering at whatever
     * rate it last had.
     */
    @Transaction
    suspend fun replaceForBase(base: String, entities: List<ExchangeRateEntity>) {
        deleteByBase(base)
        upsertAll(entities)
    }
}
