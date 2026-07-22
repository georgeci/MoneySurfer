package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

data class Category(
    val id: CategoryId,
    val workspaceId: WorkspaceId,
    val name: String,
    val type: CategoryType,
    val parentId: CategoryId?,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val systemKind: CategorySystemKind? = null,
    /**
     * Semantic icon key from [CategoryAppearance.ICON_KEYS] — not a palette index, so the
     * picker grid can be reordered without re-icing every saved category. Defaults to the
     * deterministic choice for this id, which is also what the backfill writes.
     */
    val iconKey: String = CategoryAppearance.defaultIconKey(id.value, systemKind),
    /** Hue in degrees, 0 until 360. Resolved to the nearest palette tint at render time. */
    val hue: Int = CategoryAppearance.defaultHue(id.value, systemKind),
)
