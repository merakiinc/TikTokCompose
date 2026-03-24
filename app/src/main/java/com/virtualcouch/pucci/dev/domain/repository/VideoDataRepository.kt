package com.virtualcouch.pucci.dev.domain.repository

import com.virtualcouch.pucci.dev.domain.models.VideoData
import kotlinx.coroutines.flow.Flow

data class VideoListResult(
    val videos: List<VideoData>,
    val nextToken: String?
)

interface VideoDataRepository {
    fun fetchData(after: String? = null): Flow<VideoListResult>
}
