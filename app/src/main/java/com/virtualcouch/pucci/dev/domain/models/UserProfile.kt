package com.virtualcouch.pucci.dev.domain.models

data class UserProfile(
    val name: String,
    val username: String,
    val avatarUrl: String?,
    val followersCount: String,
    val followingCount: String,
    val likesCount: String
)
