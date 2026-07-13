package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationFeatureConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Online build keeps every transaction kind, including multi-account
 * transfers. Mirrors `composeAppOffline`'s `transferEnabled = false` override;
 * keeping the binding host-owned (instead of defaulted in
 * `feature/transaction`) means the offline build can't silently regress to
 * showing the Transfer segment through module load order changes.
 */
@Module
class OnlineTransactionCreationModule {

    @Single
    fun transactionCreationFeatureConfig(): TransactionCreationFeatureConfig =
        TransactionCreationFeatureConfig(transferEnabled = true)
}
