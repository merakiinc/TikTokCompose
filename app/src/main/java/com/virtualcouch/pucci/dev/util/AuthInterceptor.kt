package com.virtualcouch.pucci.dev.util

import com.virtualcouch.pucci.dev.data.api.AuthApi
import com.virtualcouch.pucci.dev.data.api.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val authApiProvider: Provider<AuthApi>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // REGRA CRÍTICA: Só adiciona headers se for para a nossa API
        // URLs do Cloudflare R2 ou S3 NÃO podem receber estes headers extras
        if (!originalRequest.url.host.contains("pucci.dev")) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "VirtualCouchMobile/1.0")
            .header("Accept", "application/json")

        // Add Bearer token if available and not already present
        val token = tokenManager.getAccessToken()
        if (token != null && originalRequest.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Handle 401 Unauthorized
        if (response.code == 401) {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken != null) {
                response.close() 

                val refreshResponse = runBlocking {
                    try {
                        authApiProvider.get().refreshTokens(RefreshTokenRequest(refreshToken))
                    } catch (e: Exception) {
                        null
                    }
                }

                if (refreshResponse != null && refreshResponse.isSuccessful && refreshResponse.body() != null) {
                    val newTokens = refreshResponse.body()!!
                    tokenManager.saveTokens(newTokens.access.token, newTokens.refresh.token)

                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.access.token}")
                        .build()
                    return chain.proceed(newRequest)
                } else {
                    tokenManager.clearTokens()
                }
            }
        }

        return response
    }
}
