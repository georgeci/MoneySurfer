package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import io.github.irgaly.navigation3.resultstate.SerializableNavigationResultKey

val CategoryPickerResultKey: SerializableNavigationResultKey<CategoryId> =
    SerializableNavigationResultKey(
        serializer = CategoryId.serializer(),
        resultKey = "CategoryPickerResult",
    )
