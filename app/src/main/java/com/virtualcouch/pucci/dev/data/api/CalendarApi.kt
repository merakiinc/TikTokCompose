package com.virtualcouch.pucci.dev.data.api

import com.virtualcouch.pucci.dev.data.models.CalendarEvent
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface CalendarApi {
    @GET("v1/calendar/events")
    suspend fun getEvents(
        @Header("Authorization") bearerToken: String
    ): Response<List<CalendarEvent>>
}
