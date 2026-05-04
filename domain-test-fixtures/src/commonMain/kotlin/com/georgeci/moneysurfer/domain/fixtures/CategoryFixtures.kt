package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId

fun aCategory(
    id: CategoryId = categoryId(),
    workspaceId: WorkspaceId = workspaceId(),
    name: String = "Food",
    type: CategoryType = CategoryType.EXPENSE,
    parentId: CategoryId? = null,
    createdAt: Long = TEST_EPOCH_MILLIS,
): Category = Category(
    id = id,
    workspaceId = workspaceId,
    name = name,
    type = type,
    parentId = parentId,
    createdAt = createdAt,
)
