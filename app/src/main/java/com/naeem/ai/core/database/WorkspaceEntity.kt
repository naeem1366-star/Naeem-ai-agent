package com.naeem.ai.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workspaces",
    indices = [Index(value = ["ownerProfileId"]), Index(value = ["kind"])]
)
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** personal, family, trip, or project */
    val kind: String,
    /** Null only for system-owned workspaces. */
    val ownerProfileId: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
