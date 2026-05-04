package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

data class Category(
    val id: CategoryId,
    val workspaceId: WorkspaceId,
    val name: String,
    val type: CategoryType,
    val parentId: CategoryId?,
    val createdAt: Long,
)
