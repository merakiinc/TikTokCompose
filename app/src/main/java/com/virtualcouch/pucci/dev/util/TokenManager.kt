package com.virtualcouch.pucci.dev.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "virtual_couch_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCESS_EXPIRES = "access_expires"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_REFRESH_EXPIRES = "refresh_expires"
    }

    fun saveTokens(
        accessToken: String, 
        accessTokenExpires: String, 
        refreshToken: String, 
        refreshTokenExpires: String
    ) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_ACCESS_EXPIRES, accessTokenExpires)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_REFRESH_EXPIRES, refreshTokenExpires)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    // Extrai o ID do usuário (campo 'sub') de dentro do JWT
    fun getUserId(): String? {
        val token = getAccessToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.DEFAULT))
            val json = JSONObject(payload)
            json.optString("sub")
        } catch (e: Exception) {
            null
        }
    }

    fun isAccessTokenExpired(): Boolean {
        val expiresAt = prefs.getString(KEY_ACCESS_EXPIRES, null) ?: return true
        return try {
            val expirationDate = ZonedDateTime.parse(expiresAt).toInstant()
            Instant.now().isAfter(expirationDate.minusSeconds(60))
        } catch (e: Exception) {
            true
        }
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }

    fun hasRefreshToken(): Boolean = getRefreshToken() != null
    
    fun hasAccessToken(): Boolean = getAccessToken() != null && !isAccessTokenExpired()
}
