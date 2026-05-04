package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getAll(): Flow<List<User>>
    suspend fun getById(id: UserId): User?
    suspend fun insert(user: User)
    suspend fun update(user: User)
    suspend fun upsert(user: User)
    suspend fun delete(id: UserId)
}
