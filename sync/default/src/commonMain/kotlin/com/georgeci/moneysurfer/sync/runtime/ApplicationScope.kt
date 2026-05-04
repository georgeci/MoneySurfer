package com.georgeci.moneysurfer.sync.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single

/**
 * Process-bound coroutine scope for the sync subsystem. Lives for the lifetime
 * of the app process, never cancelled in the normal flow.
 *
 * Uses [SupervisorJob] so a failure in one child does not cancel sibling
 * collectors of `state` / `lastOutcome`.
 *
 * Tests substitute this via constructor injection (pass a `TestScope` as `delegate`).
 * See SyncCoordinatorFAQ.md №17.
 */
@Single
class ApplicationScope(
    private val delegate: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : CoroutineScope by delegate
