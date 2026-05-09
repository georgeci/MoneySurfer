package com.georgeci.moneysurfer.feature.transaction.creation

/**
 * Per-host visibility switches for the transaction creation screen. The online
 * build keeps every transaction kind; the offline build (`composeAppOffline`)
 * drops [transferEnabled] because multi-account transfers are out of the
 * offline MVP scope.
 *
 * Each host registers its own instance in Koin (see `OnlineTransactionCreationModule`
 * / `offlineTransactionCreationModule`) so the offline configuration cannot
 * silently regress via Koin module load order.
 */
data class TransactionCreationFeatureConfig(
    val transferEnabled: Boolean = true,
)
