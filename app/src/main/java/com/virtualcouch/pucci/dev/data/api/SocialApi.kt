package com.virtualcouch.pucci.dev.data.api

import com.squareup.moshi.JsonClass
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class PresignedUrlResponse(
    val uploadUrl: String,
    val fileKey: String,
    val postId: String,
    val publicUrl: String
)

@JsonClass(generateAdapter = true)
data class PostVideoRequest(
    val postId: String,
    val videoUrl: String,
    val content: String,
    val locale: String
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    val id: String?, // Adicionado ID para comparação
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val followersCount: String,
    val followingCount: String,
    val likesCount: String
)

@JsonClass(generateAdapter = true)
data class UserVideoResponse(
    val id: String,
    val videoUrl: String,
    val thumbnailUrl: String?,
    val content: String?,
    val likes: String?,
    val comments: String?,
    val shares: String?
)

@JsonClass(generateAdapter = true)
data class InteractionRequest(
    val postId: String,
    val type: String, 
    val weight: Int
)

@JsonClass(generateAdapter = true)
data class FeedResponse(
    val videos: List<FeedVideo>,
    val nextToken: String?
)

@JsonClass(generateAdapter = true)
data class FeedVideo(
    val id: String,
    val videoUrl: String,
    val hlsUrl: String?,
    val dashUrl: String?,
    val thumbnailUrl: String?,
    val aspectRatio: Float?,
    val author: FeedAuthor,
    val description: String?,
    val stats: FeedStats,
    val userStatus: FeedUserStatus
)

@JsonClass(generateAdapter = true)
data class FeedAuthor(
    val id: String,
    val name: String?,
    val username: String?,
    val avatarUrl: String?
)

@JsonClass(generateAdapter = true)
data class FeedStats(
    val likes: String,
    val comments: String,
    val shares: String
)

@JsonClass(generateAdapter = true)
data class FeedUserStatus(
    val isLiked: Boolean,
    val isFollowing: Boolean
)

@JsonClass(generateAdapter = true)
data class AuthorProfileResponse(
    val id: String,
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String?,
    val link: String?,
    val stats: AuthorStats,
    val isFollowing: Boolean,
    val videos: List<UserVideoResponse>
)

@JsonClass(generateAdapter = true)
data class AuthorStats(
    val followersCount: String,
    val followingCount: String,
    val likesCount: String
)

@JsonClass(generateAdapter = true)
data class AuthorLink(
    val label: String,
    val url: String
)

interface SocialApi {
    @GET("v1/social/presigned-url")
    suspend fun getPresignedUrl(
        @Query("fileName") fileName: String = "video.mp4",
        @Query("contentType") contentType: String = "video/mp4"
    ): Response<PresignedUrlResponse>

    @PUT
    suspend fun uploadToCloud(
        @Url url: String,
        @Body video: RequestBody
    ): Response<Unit>

    @POST("v1/social/post-video")
    suspend fun postVideoConfirm(
        @Body request: PostVideoRequest
    ): Response<Unit>

    @GET("v1/social/profile")
    suspend fun getUserProfile(): Response<UserProfileResponse>

    @GET("v1/social/my-videos")
    suspend fun getUserVideos(): Response<List<UserVideoResponse>>

    @POST("v1/social/interaction")
    suspend fun recordInteraction(
        @Body request: InteractionRequest
    ): Response<Unit>

    @GET("v1/social/feed-videos")
    suspend fun getForYouFeed(
        @Query("after") after: String? = null
    ): Response<FeedResponse>

    @GET("v1/social/feed-videos-following")
    suspend fun getFollowingFeed(
        @Query("after") after: String? = null
    ): Response<FeedResponse>

    @GET("v1/social/author-profile/{userId}")
    suspend fun getAuthorProfile(
        @Path("userId") userId: String
    ): Response<AuthorProfileResponse>
}
