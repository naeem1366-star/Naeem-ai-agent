package com.naeem.ai.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces WHERE ownerProfileId = :profileId OR ownerProfileId IS NULL ORDER BY createdAt DESC")
    fun observeForProfile(profileId: String): Flow<List<WorkspaceEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET name = :name, updatedAt = :updatedAt WHERE id = :workspaceId")
    suspend fun rename(workspaceId: String, name: String, updatedAt: Long = System.currentTimeMillis())
}
