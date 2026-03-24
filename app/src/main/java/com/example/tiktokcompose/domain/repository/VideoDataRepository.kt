package com.example.tiktokcompose.domain.repository

import com.example.tiktokcompose.domain.models.VideoData
import kotlinx.coroutines.flow.Flow

data class VideoListResult(
    val videos: List<VideoData>,
    val nextToken: String?
)

interface VideoDataRepository {
    fun fetchData(after: String? = null): Flow<VideoListResult>
}
