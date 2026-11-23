package com.naeem.ai.core.profiles

import com.naeem.ai.core.database.NaeemDatabase
import com.naeem.ai.core.database.ProfileEntity
import com.naeem.ai.core.database.WorkspaceEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val database: NaeemDatabase) {
    fun observeProfiles(): Flow<List<ProfileEntity>> =
        database.profileDao().observeProfiles()

    fun observeWorkspaces(profileId: String): Flow<List<WorkspaceEntity>> =
        database.workspaceDao().observeForProfile(profileId)

    suspend fun createProfile(displayName: String, isOwner: Boolean = false): String {
        require(displayName.isNotBlank()) { "نام پروفایل نمی‌تواند خالی باشد." }
        val id = UUID.randomUUID().toString()
        database.profileDao().insert(
            ProfileEntity(id = id, displayName = displayName.trim(), isOwner = isOwner)
        )
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = UUID.randomUUID().toString(),
                name = "فضای شخصی",
                kind = "personal",
                ownerProfileId = id
            )
        )
        return id
    }

    suspend fun createWorkspace(profileId: String, name: String, kind: String): String {
        require(name.isNotBlank()) { "نام فضا نمی‌تواند خالی باشد." }
        require(kind in setOf("personal", "family", "trip", "project")) {
            "نوع فضای کاری معتبر نیست."
        }
        val id = UUID.randomUUID().toString()
        database.workspaceDao().insert(
            WorkspaceEntity(
                id = id,
                name = name.trim(),
                kind = kind,
                ownerProfileId = profileId
            )
        )
        return id
    }
}
