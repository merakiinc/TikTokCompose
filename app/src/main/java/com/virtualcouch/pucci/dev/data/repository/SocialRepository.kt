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
                        description = it.content ?: "",
                        likes = it.likes ?: "0",
                        comments = it.comments ?: "0",
                        shares = it.shares ?: "0"
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
                    link = data.link,
                    followersCount = data.stats.followersCount,
                    followingCount = data.stats.followingCount,
                    likesCount = data.stats.likesCount,
                    isFollowing = data.isFollowing
                )
                val videos = data.videos.map { 
                    VideoData(
                        id = it.id,
                        authorId = data.id,
                        mediaUri = it.videoUrl,
                        previewImageUri = it.thumbnailUrl,
                        authorName = data.username ?: "Psicólogo",
                        description = it.content ?: "",
                        likes = it.likes ?: "0",
                        comments = it.comments ?: "0",
                        shares = it.shares ?: "0"
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
        } catch (e: Exception) {}
    }
}
