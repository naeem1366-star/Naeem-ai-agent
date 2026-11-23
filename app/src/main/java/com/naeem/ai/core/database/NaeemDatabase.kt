package com.naeem.ai.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProfileEntity::class, WorkspaceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NaeemDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        @Volatile private var INSTANCE: NaeemDatabase? = null

        fun getInstance(context: Context): NaeemDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NaeemDatabase::class.java,
                    "naeem_ai_v2.db"
                ).build().also { INSTANCE = it }
            }
    }
}
