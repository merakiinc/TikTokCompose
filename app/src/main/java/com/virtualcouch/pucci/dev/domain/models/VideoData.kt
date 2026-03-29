package com.virtualcouch.pucci.dev.domain.models

data class VideoData(
    val id: String,
    val mediaUri: String,
    val previewImageUri: String?,
    val aspectRatio: Float? = null,
    val authorName: String = "Psicólogo(a)",
    val authorAvatar: String = "https://api.dicebear.com/7.x/avataaars/svg?seed=Avatar",
    val description: String = "",
    val likes: String = "0",
    val comments: String = "0",
    val shares: String = "0",
    val isLiked: Boolean = false,
    val isFollowing: Boolean = false
)
