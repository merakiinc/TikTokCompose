package com.virtualcouch.pucci.dev.domain.models

data class VideoData(
    val id: String,
    val mediaUri: String,
    val previewImageUri: String,
    val aspectRatio: Float? = null
)
