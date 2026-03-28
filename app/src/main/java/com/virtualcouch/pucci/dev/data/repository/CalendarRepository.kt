package com.virtualcouch.pucci.dev.data.repository

import android.util.Log
import com.virtualcouch.pucci.dev.data.api.CalendarApi
import com.virtualcouch.pucci.dev.data.local.dao.EventDao
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity
import com.virtualcouch.pucci.dev.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarApi: CalendarApi,
    private val eventDao: EventDao,
    private val tokenManager: TokenManager
) {
    private val tag = "CalendarRepository"

    private val _allEvents = MutableStateFlow<List<EventEntity>>(emptyList())
    val allEvents: Flow<List<EventEntity>> = _allEvents.asStateFlow()

    suspend fun syncEvents() {
        val token = tokenManager.getAccessToken() ?: return
        
        withContext(Dispatchers.IO) {
            _allEvents.value = eventDao.getAllEvents()
        }

        try {
            val response = calendarApi.getEvents("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val entities = response.body()!!.map { event ->
                    EventEntity(
                        id = event.id,
                        title = event.title,
                        description = event.description ?: "",
                        startTime = event.startTime,
                        endTime = event.endTime,
                        patientName = event.patientName ?: ""
                    )
                }
                withContext(Dispatchers.IO) {
                    eventDao.clearAll()
                    eventDao.insertEvents(entities)
                    _allEvents.value = eventDao.getAllEvents()
                }
                Log.d(tag, "Sync successful: ${entities.size} events")
            } else {
                Log.e(tag, "Sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Sync error", e)
        }
    }
}
