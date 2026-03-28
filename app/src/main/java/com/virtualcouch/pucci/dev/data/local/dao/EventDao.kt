package com.virtualcouch.pucci.dev.data.local.dao

import androidx.room.*
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun getAllEvents(): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEvents(events: List<EventEntity>): LongArray

    @Query("DELETE FROM events")
    fun clearAll(): Int
}
