package com.georgeci.moneysurfer.domain

/** Carries platform-supplied app metadata into shared code. Bound by each platform module. */
data class AppInfo(
    val version: String,
    /**
     * Monotonically increasing numeric build identifier — used by the
     * remote app-version gate (see block.md). Android: `versionCode`.
     * Other platforms compute an equivalent integer.
     */
    val versionCode: Int,
)
