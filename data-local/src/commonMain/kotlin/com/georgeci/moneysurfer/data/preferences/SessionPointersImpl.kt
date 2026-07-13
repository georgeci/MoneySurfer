package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.data.datastore.UiSettingsDataSource
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import org.koin.core.annotation.Single

@Single(binds = [SessionPointers::class])
class SessionPointersImpl(
    src: UiSettingsDataSource,
) : SessionPointers {

    override val currentUserId: Pref<UserId?> = src.currentUserId.asPref(
        fromStored = { stored -> if (stored.isEmpty()) null else UserId(stored) },
        toStored = { value -> value?.value.orEmpty() },
    )

    override val currentWorkspaceId: Pref<WorkspaceId?> = src.currentWorkspaceId.asPref(
        fromStored = { stored -> if (stored.isEmpty()) null else WorkspaceId(stored) },
        toStored = { value -> value?.value.orEmpty() },
    )

    override val currentFirebaseUid: Pref<String?> = src.firebaseUid.asPref(
        fromStored = { stored -> stored.ifEmpty { null } },
        toStored = { value -> value.orEmpty() },
    )

    override val hasUsedDemo: Pref<Boolean> = src.hasUsedDemo.asPref()

    override val currencyChosen: Pref<Boolean> = src.currencyChosen.asPref()

    override val onboardingSkipped: Pref<Boolean> = src.onboardingSkipped.asPref()
}
