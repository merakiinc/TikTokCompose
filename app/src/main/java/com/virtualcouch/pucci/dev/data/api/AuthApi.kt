package com.virtualcouch.pucci.dev.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val user: UserData,
    val tokens: TokenData
) {
    @JsonClass(generateAdapter = true)
    data class UserData(
        val id: String,
        val email: String,
        val name: String,
        val role: String
    )

    @JsonClass(generateAdapter = true)
    data class TokenData(
        val access: TokenInfo,
        val refresh: TokenInfo
    ) {
        @JsonClass(generateAdapter = true)
        data class TokenInfo(
            val token: String,
            val expires: String
        )
    }
}

interface AuthApi {
    @POST("v1/auth/login-mobile")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>
}
