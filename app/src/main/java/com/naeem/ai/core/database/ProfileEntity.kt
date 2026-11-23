package com.naeem.ai.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["createdAt"])]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val avatarColor: Long = 0xFF72DDB7,
    val isOwner: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
