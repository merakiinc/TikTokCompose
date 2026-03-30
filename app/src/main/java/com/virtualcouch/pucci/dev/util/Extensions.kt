package com.virtualcouch.pucci.dev.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.virtualcouch.pucci.dev.domain.models.VideoData

fun List<VideoData>.toMediaItems(): List<MediaItem> {
    return map { video ->
        val cleanUrl = video.mediaUri.trim()
        val builder = MediaItem.Builder().setUri(cleanUrl)
        
        // Adiciona dica de formato para o player carregar instantaneamente
        when {
            cleanUrl.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            cleanUrl.contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        
        builder.build()
    }
}

fun showToast(context: Context, message: String?) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
