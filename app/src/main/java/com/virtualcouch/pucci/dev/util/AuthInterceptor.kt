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
        
        if (!originalRequest.url.host.contains("pucci.dev")) {
            return chain.proceed(originalRequest)
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("User-Agent", "VirtualCouchMobile/1.0")
            .header("Accept", "application/json")

        val token = tokenManager.getAccessToken()
        if (token != null && originalRequest.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401) {
            val savedRefreshToken = tokenManager.getRefreshToken()
            if (savedRefreshToken != null) {
                // Tenta refresh
                val refreshResponse = runBlocking {
                    try {
                        val api = authApiProvider.get()
                        api.refreshTokens(RefreshTokenRequest(refreshToken = savedRefreshToken))
                    } catch (e: Exception) {
                        null
                    }
                }

                if (refreshResponse != null && refreshResponse.isSuccessful && refreshResponse.body() != null) {
                    // Se deu certo o refresh, fecha a anterior e tenta de novo com o novo token
                    response.close() 
                    
                    val newTokens = refreshResponse.body()!!
                    tokenManager.saveTokens(
                        accessToken = newTokens.access.token,
                        accessTokenExpires = newTokens.access.expires,
                        refreshToken = newTokens.refresh.token,
                        refreshTokenExpires = newTokens.refresh.expires
                    )

                    val newRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${newTokens.access.token}")
                        .build()
                    return chain.proceed(newRequest)
                } else {
                    // Se falhou o refresh, limpa e retorna a resposta 401 original (AINDA ABERTA)
                    tokenManager.clearTokens()
                }
            }
        }

        return response
    }
}
