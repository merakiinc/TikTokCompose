package com.virtualcouch.pucci.dev.data.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SocialApi {
    @Multipart
    @POST("v1/social/post-video")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
        @Part("locale") locale: okhttp3.RequestBody
    ): Response<Unit>
}
