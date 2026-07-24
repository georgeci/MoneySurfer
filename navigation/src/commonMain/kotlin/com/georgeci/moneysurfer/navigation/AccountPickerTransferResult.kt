package com.georgeci.moneysurfer.navigation

import io.github.irgaly.navigation3.resultstate.SerializableNavigationResultKey
import kotlinx.serialization.builtins.serializer

/**
 * Set by the account-chooser sheet when the user takes its "Transfer between accounts instead"
 * footer. The value carries no data — the host reads it as "switch this entry to a transfer".
 */
val AccountPickerTransferResultKey: SerializableNavigationResultKey<Boolean> =
    SerializableNavigationResultKey(
        serializer = Boolean.serializer(),
        resultKey = "AccountPickerTransferResult",
    )
