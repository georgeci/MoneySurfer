package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.primitives.AccountId
import io.github.irgaly.navigation3.resultstate.SerializableNavigationResultKey

val AccountPickerResultKey: SerializableNavigationResultKey<AccountId> =
    SerializableNavigationResultKey(
        serializer = AccountId.serializer(),
        resultKey = "AccountPickerResult",
    )
