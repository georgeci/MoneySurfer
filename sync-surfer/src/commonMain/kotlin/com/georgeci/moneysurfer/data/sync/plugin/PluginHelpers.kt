package com.georgeci.moneysurfer.data.sync.plugin
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.ConflictResolution

internal suspend inline fun <T : Any> applyResolution(
    resolution: ConflictResolution<T>,
    crossinline write: suspend (T) -> Unit,
): EntityApplyResult = when (resolution) {
    is ConflictResolution.TakeRemote -> {
        write(resolution.value)
        EntityApplyResult(applied = true, wasConflict = false)
    }
    is ConflictResolution.Merged -> {
        write(resolution.value)
        EntityApplyResult(applied = true, wasConflict = true)
    }
    is ConflictResolution.TakeLocal -> EntityApplyResult(applied = false, wasConflict = true)
    ConflictResolution.Skip -> EntityApplyResult(applied = false, wasConflict = true)
}
