package com.virtualcouch.pucci.dev.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.virtualcouch.pucci.dev.data.local.dao.EventDao
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
