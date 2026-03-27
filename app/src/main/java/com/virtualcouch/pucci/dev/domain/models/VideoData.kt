package com.virtualcouch.pucci.dev.domain.models

data class VideoData(
    val id: String,
    val mediaUri: String,
    val previewImageUri: String,
    val aspectRatio: Float? = null,
    val authorName: String = "Psicólogo(a)",
    val authorAvatar: String = "https://api.dicebear.com/7.x/avataaars/svg?seed=Avatar",
    val likes: String = "1.2k",
    val comments: String = "85",
    val shares: String = "12",
    val isLiked: Boolean = false
)
