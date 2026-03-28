package com.virtualcouch.pucci.dev.data.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String?,
    val startTime: String,
    val endTime: String,
    val patientName: String?
)
