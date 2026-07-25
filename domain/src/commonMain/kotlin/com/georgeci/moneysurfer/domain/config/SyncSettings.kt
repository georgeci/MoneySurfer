package com.georgeci.moneysurfer.domain.config

import kotlinx.coroutines.flow.Flow

/**
 * Whether sync may run at all. Replaces `SyncFeatureFlag`.
 *
 * Gates the Settings → Sync screen, the manual "Force sync" action, the in-process periodic
 * ticker, and every use-case-driven trigger (post-auth bootstrap, create-workspace push,
 * accept-invite hydration, incoming-invite refresh).
 *
 * [isEnabled] combines **three** terms — build AND server kill switch AND user toggle. The build
 * term matters: sync is deliberately off in both hosts today because the feature is not shipped,
 * and a server/user pair would have no slot for that, so dropping it would switch sync on.
 *
 * A flow rather than a `Boolean`, because a server-side kill switch can retract mid-session.
 */
interface SyncSettings {
    val isEnabled: Flow<Boolean>
}
