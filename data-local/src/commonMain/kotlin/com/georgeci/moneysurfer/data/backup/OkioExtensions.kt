package com.georgeci.moneysurfer.data.backup

import okio.FileSystem
import okio.Path

/** okio has no `deleteIfExists`; `delete(mustExist = false)` is its spelling of the same thing. */
internal fun FileSystem.deleteIfExists(path: Path) = delete(path, mustExist = false)
