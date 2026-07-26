package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

/**
 * Drops all locally-stored data accumulated during a demo session.
 *
 * Invoked by real-auth use cases ([LoginUseCase] / [SignupUseCase] /
 * [AnonymousLoginUseCase]) **after** Firebase auth succeeds and **before**
 * `PostAuthBootstrapUseCase` so the real session starts on a clean slate.
 *
 * No-op when `hasUsedDemo` flag is unset — keeps repeated login flows cheap.
 *
 * Demo data is intentionally never pushed to Firestore (the outbox guard in
 * `OutboxEnqueuer` blocks enqueue without a Firebase uid), so the wipe here
 * has no remote-cleanup counterpart. See sync.md §2.11.
 */
@Single
class WipeDemoDataUseCase(
    private val localDataResetRepository: LocalDataResetRepository,
    private val session: SessionPointers,
    private val sessionMutator: SessionMutator,
) {
    suspend operator fun invoke() {
        if (!session.hasUsedDemo.first()) return
        localDataResetRepository.clearAll()
        sessionMutator.setHasUsedDemo(false)
    }
}
