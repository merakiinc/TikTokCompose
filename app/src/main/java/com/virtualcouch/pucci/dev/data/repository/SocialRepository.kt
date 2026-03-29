package com.virtualcouch.pucci.dev.data.repository

import com.virtualcouch.pucci.dev.data.api.SocialApi
import com.virtualcouch.pucci.dev.data.api.UserProfileResponse
import com.virtualcouch.pucci.dev.data.api.InteractionRequest
import com.virtualcouch.pucci.dev.domain.models.VideoData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val api: SocialApi // O Hilt usará o provider padrão (sem @Named) do NetworkModule
) {
    suspend fun getUserProfile(): UserProfileResponse? {
        return try {
            val response = api.getUserProfile()
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserVideos(): List<VideoData> {
        return try {
            val response = api.getUserVideos()
            if (response.isSuccessful) {
                response.body()?.map { 
                    VideoData(
                        id = it.id,
                        mediaUri = it.videoUrl,
                        previewImageUri = it.thumbnailUrl,
                        authorName = "Eu",
                        likes = it.likes,
                        comments = it.comments,
                        shares = it.shares
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun recordInteraction(postId: String, type: String, weight: Int) {
        try {
            api.recordInteraction(InteractionRequest(postId, type, weight))
        } catch (e: Exception) {
            // Silently fail for analytics
        }
    }
}
