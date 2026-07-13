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
 * sign-in screen. The result: a clean install lands directly on a populated Dashboard.
 *
 * Idempotency is delegated to:
 *  - [DemoLoginUseCase] uses a deterministic local user id, so re-running it just reasserts the
 *    same row.
 *  - [SeedDefaultsUseCase] handles both the first-run and the *repair* path (workspace pinned
 *    but defaults missing) — we always invoke it and let it decide whether anything is needed.
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
        // A null workspace pointer before the seed means this is a fresh install — the seed
        // is about to create a workspace with a locale-derived currency. Flip `currencyChosen`
        // so `AppLaunchViewModel` routes to the currency picker for explicit confirmation.
        val freshInstall = session.currentWorkspaceId.flow.first() == null
        seedDefaultsUseCase(CurrencyDefaults.systemDefault())
        if (freshInstall && session.currentWorkspaceId.flow.first() != null) {
            session.currencyChosen.set(false)
            log.i { "[seed] fresh install — currency picker pending" }
        }
    }

    private companion object {
        const val TAG = "OfflineFirstRun"
    }
}
