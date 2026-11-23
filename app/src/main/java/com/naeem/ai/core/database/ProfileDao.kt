package com.naeem.ai.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY isOwner DESC, createdAt ASC")
    fun observeProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfile(profileId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: ProfileEntity)

    @Query("UPDATE profiles SET displayName = :name, updatedAt = :updatedAt WHERE id = :profileId")
    suspend fun rename(profileId: String, name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM profiles WHERE id = :profileId AND isOwner = 0")
    suspend fun deleteNonOwner(profileId: String)
}
