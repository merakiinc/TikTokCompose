package com.virtualcouch.pucci.dev.util

import android.util.Log
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

    private val tag = "AuthInterceptor"

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        if (!originalRequest.url.host.contains("pucci.dev")) {
            return chain.proceed(originalRequest)
        }

        // 1. Tenta a requisição original
        val response = chain.proceed(buildRequestWithToken(originalRequest))

        // 2. Se deu 401, precisamos de um novo token
        if (response.code == 401) {
            synchronized(this) {
                // Checa se outro thread já atualizou o token enquanto este esperava o lock
                val currentToken = tokenManager.getAccessToken()
                val requestToken = originalRequest.header("Authorization")?.removePrefix("Bearer ")
                
                // Se o token no storage já é diferente do que usamos nesta requisição, 
                // significa que o refresh JÁ aconteceu em outro thread.
                if (currentToken != null && currentToken != requestToken) {
                    Log.i(tag, "Token already refreshed by another thread. Retrying with new token.")
                    response.close()
                    return chain.proceed(buildRequestWithToken(originalRequest))
                }

                // Se ainda é o mesmo token, então este thread fará o refresh
                Log.w(tag, "Token expired (401). Attempting synchronized refresh...")
                val savedRefreshToken = tokenManager.getRefreshToken()
                
                if (savedRefreshToken != null) {
                    val refreshResponse = runBlocking {
                        try {
                            authApiProvider.get().refreshTokens(RefreshTokenRequest(refreshToken = savedRefreshToken))
                        } catch (e: Exception) {
                            Log.e(tag, "Critical error during refresh call", e)
                            null
                        }
                    }

                    if (refreshResponse != null && refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        val newTokens = refreshResponse.body()!!
                        Log.i(tag, "Refresh successful. Saving new tokens.")
                        
                        tokenManager.saveTokens(
                            accessToken = newTokens.access.token,
                            accessTokenExpires = newTokens.access.expires,
                            refreshToken = newTokens.refresh.token,
                            refreshTokenExpires = newTokens.refresh.expires
                        )

                        response.close()
                        return chain.proceed(buildRequestWithToken(originalRequest))
                    } else {
                        Log.e(tag, "Refresh FAILED (code: ${refreshResponse?.code()}). Logging out user.")
                        tokenManager.clearTokens()
                    }
                } else {
                    Log.w(tag, "No refresh token found. Can't recover from 401.")
                }
            }
        }

        return response
    }

    private fun buildRequestWithToken(request: okhttp3.Request): okhttp3.Request {
        val builder = request.newBuilder()
            .header("User-Agent", "VirtualCouchMobile/1.0")
            .header("Accept", "application/json")
        
        val token = tokenManager.getAccessToken()
        if (token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }
}
