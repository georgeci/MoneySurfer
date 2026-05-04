package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlin.time.Instant

data class Workspace(
    val id: WorkspaceId,
    val name: String,
    val description: String,
    val baseCurrency: CurrencyCode,
    val ownerId: UserId,
    val createdAt: Instant,
    val archived: Boolean = false,
)
