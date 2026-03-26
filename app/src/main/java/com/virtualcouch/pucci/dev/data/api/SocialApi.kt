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
}
