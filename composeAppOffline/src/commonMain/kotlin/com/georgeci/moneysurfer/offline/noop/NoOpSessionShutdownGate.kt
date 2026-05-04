package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate

/**
 * Offline build has no in-flight sync or background scheduler to tear
 * down — logout's session shutdown is a no-op.
 */
class NoOpSessionShutdownGate : SessionShutdownGate {
    override suspend fun shutdown() = Unit
}
