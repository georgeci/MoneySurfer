package com.georgeci.moneysurfer.offline.firstrun

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.formatter.CurrencyDefaults
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.SeedDefaultsUseCase
import kotlinx.coroutines.flow.first

/**
 * Offline-build first-run seed. Pre-seeds an anonymous "demo" user so that [SeedDefaultsUseCase]
 * (which requires `currentUserId`) can run without the user having to tap a button on the
 * sign-in screen, plus the workspace + default categories the rest of the app needs.
 *
 * Accounts are deliberately *not* seeded: after onboarding the user creates the first account
 * themselves (`Route.AccountCreation(firstRun = true)`), and the currency they pick there becomes
 * the workspace base currency.
 *
 * Idempotency is delegated to:
 *  - [DemoLoginUseCase] uses a deterministic local user id, so re-running it just reasserts the
 *    same row.
 *  - [SeedDefaultsUseCase] is a no-op once a workspace is pinned.
 */
class OfflineFirstRunSeeder(
    private val session: SessionPointers,
    private val demoLoginUseCase: DemoLoginUseCase,
    private val seedDefaultsUseCase: SeedDefaultsUseCase,
) : FirstRunSeeder {

    private val log = Logger.withTag(TAG)

    override suspend fun seedIfNeeded() {
        if (session.currentUserId.flow.first() == null) {
            demoLoginUseCase().onLeft { err ->
                log.w { "[abort] DemoLogin failed: $err" }
                return
            }
        }
        // The locale-derived currency is only a placeholder for the workspace: the first-run
        // account screen overwrites it with whatever the user picks there.
        seedDefaultsUseCase(CurrencyDefaults.systemDefault(), seedCashAccount = false)
    }

    private companion object {
        const val TAG = "OfflineFirstRun"
    }
}
