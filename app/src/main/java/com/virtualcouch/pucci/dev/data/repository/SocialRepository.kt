package com.virtualcouch.pucci.dev.data.repository

import com.virtualcouch.pucci.dev.data.api.SocialApi
import com.virtualcouch.pucci.dev.data.api.UserProfileResponse
import com.virtualcouch.pucci.dev.data.api.InteractionRequest
import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.domain.models.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val api: SocialApi 
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
                        description = it.content,
                        likes = it.likes,
                        comments = it.comments,
                        shares = it.shares,
                        isLiked = false,
                        isFollowing = false
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAuthorProfile(userId: String): Pair<UserProfile, List<VideoData>>? {
        return try {
            val response = api.getAuthorProfile(userId)
            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val profile = UserProfile(
                    id = data.id,
                    name = data.name,
                    username = data.username,
                    avatarUrl = data.avatarUrl,
                    bio = data.bio,
                    links = data.links,
                    followersCount = data.stats.likes, // Backend deve mapear corretamente
                    followingCount = data.stats.shares,
                    likesCount = data.stats.comments,
                    isFollowing = data.isFollowing
                )
                val videos = data.videos.map { 
                    VideoData(
                        id = it.id,
                        mediaUri = it.videoUrl,
                        previewImageUri = it.thumbnailUrl,
                        authorName = data.username ?: "Psicólogo",
                        description = it.content,
                        likes = it.likes,
                        comments = it.comments,
                        shares = it.shares
                    )
                }
                Pair(profile, videos)
            } else null
        } catch (e: Exception) {
            null
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
