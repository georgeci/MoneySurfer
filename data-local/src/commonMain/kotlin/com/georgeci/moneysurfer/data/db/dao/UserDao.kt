package com.georgeci.moneysurfer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users")
    fun getAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): UserEntity?

    @Insert
    suspend fun insert(entity: UserEntity)

    @Insert
    suspend fun insertAll(entities: List<UserEntity>)

    /**
     * Stub-insert for foreign user ids that show up via workspace members during a
     * pull. Skips the row if the id is already present so the local user's
     * `displayName` / `isAnon` fields are not overwritten with peer placeholders.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: UserEntity)

    @Update
    suspend fun update(entity: UserEntity)

    @Upsert
    suspend fun upsert(entity: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
