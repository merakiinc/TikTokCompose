package com.virtualcouch.pucci.dev.domain.models

import com.virtualcouch.pucci.dev.data.api.AuthorLink

data class UserProfile(
    val id: String? = null,
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val bio: String? = null,
    val links: List<AuthorLink>? = null,
    val followersCount: String,
    val followingCount: String,
    val likesCount: String,
    val isFollowing: Boolean = false
)
