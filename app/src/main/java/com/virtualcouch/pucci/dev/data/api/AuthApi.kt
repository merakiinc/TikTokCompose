package com.virtualcouch.pucci.dev.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val user: UserData?,
    val tokens: TokenData?,
    val phoneNumber: String?,
    val method: String?
) {
    @JsonClass(generateAdapter = true)
    data class UserData(
        val id: String,
        val email: String,
        val name: String,
        val role: String,
        val phoneNumber: String?
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

@JsonClass(generateAdapter = true)
data class SendOtpRequest(
    val phoneNumber: String,
    val method: String = "sms"
)

@JsonClass(generateAdapter = true)
data class SendOtpResponse(
    val phoneNumber: String,
    val method: String
)

@JsonClass(generateAdapter = true)
data class BaseErrorResponse(
    val code: Int?,
    val message: String?
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refreshToken: String
)

interface AuthApi {
    @POST("v1/auth/login-mobile")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("v1/auth/send-otp")
    suspend fun sendOtp(@Body request: SendOtpRequest): Response<SendOtpResponse>

    @GET("v1/auth/verify-otp-mobile")
    suspend fun verifyOtp(
        @Query("phoneNumber") phoneNumber: String,
        @Query("token") token: String
    ): Response<LoginResponse>

    @POST("v1/auth/refresh-tokens")
    suspend fun refreshTokens(@Body request: RefreshTokenRequest): Response<LoginResponse.TokenData>
}
