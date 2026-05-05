package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import org.koin.core.annotation.Single

@Single
class LogoutUseCase(
    private val sessionShutdownGate: SessionShutdownGate,
    private val authRemoteRepository: AuthRemoteRepository,
    private val localDataResetRepository: LocalDataResetRepository,
    private val remoteDataResetRepository: RemoteDataResetRepository,
    private val session: SessionPointers,
) {
    suspend operator fun invoke() {
        // Stop sync work BEFORE wiping local data — otherwise an in-flight
        // pull could re-populate Room mid-wipe and produce ghost rows.
        sessionShutdownGate.shutdown()
        session.currentUserId.set(null)
        session.currentWorkspaceId.set(null)
        session.currentFirebaseUid.set(null)
        localDataResetRepository.clearAll()
        remoteDataResetRepository.clearAll()
        // Best-effort: a failed signOut shouldn't keep the user pinned in the local session.
        authRemoteRepository.signOut()
    }
}
