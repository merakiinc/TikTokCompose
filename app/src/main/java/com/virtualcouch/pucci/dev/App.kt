package com.virtualcouch.pucci.dev

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class App : Application() {
    
    companion object {
        lateinit var videoCache: SimpleCache
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // Inicializa o cache de vídeo (100MB de limite)
        val cacheDir = File(cacheDir, "video_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        
        val evictor = LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024)
        val databaseProvider = StandaloneDatabaseProvider(this)
        
        videoCache = SimpleCache(cacheDir, evictor, databaseProvider)
    }
}
