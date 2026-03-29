package com.virtualcouch.pucci.dev.domain.repository

import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.ui.state.FeedType
import kotlinx.coroutines.flow.Flow

data class VideoListResult(
    val videos: List<VideoData>,
    val nextToken: String?
)

interface VideoDataRepository {
    fun fetchData(feedType: FeedType, after: String? = null): Flow<VideoListResult>
}
