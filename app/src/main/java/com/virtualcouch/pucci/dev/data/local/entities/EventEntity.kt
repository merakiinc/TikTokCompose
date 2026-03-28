package com.virtualcouch.pucci.dev.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val patientName: String
)
