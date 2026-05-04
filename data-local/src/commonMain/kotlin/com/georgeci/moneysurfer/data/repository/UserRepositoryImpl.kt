package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.UserDao
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.domain.model.User
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.repositories.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single(binds = [UserRepository::class])
class UserRepositoryImpl(
    private val dao: UserDao,
) : UserRepository {

    override fun getAll(): Flow<List<User>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: UserId): User? =
        dao.getById(id.value)?.toDomain()

    override suspend fun insert(user: User) {
        dao.insert(user.toEntity())
    }

    override suspend fun update(user: User) {
        dao.update(user.toEntity())
    }

    override suspend fun upsert(user: User) {
        dao.upsert(user.toEntity())
    }

    override suspend fun delete(id: UserId) {
        dao.delete(id.value)
    }
}

// Local Room storage only persists id/displayName/isAnon. The remote-only fields (email,
// workspaceIds, defaultWorkspaceId) default to empty/null on read — the Firestore record
// owns those values, the local row is just a cache + FK target.
private fun UserEntity.toDomain() = User(
    id = UserId(id),
    displayName = displayName,
    email = null,
    isAnon = isAnon,
)

private fun User.toEntity() = UserEntity(
    id = id.value,
    displayName = displayName,
    isAnon = isAnon,
)
