package com.virtualcouch.pucci.dev.domain.models

data class UserProfile(
    val id: String? = null,
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String? = null,
    val link: String? = null, // Campo de link único
    val followersCount: String,
    val followingCount: String,
    val likesCount: String,
    val isFollowing: Boolean = false
)
