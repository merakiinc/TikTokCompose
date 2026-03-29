package com.virtualcouch.pucci.dev.data.repository

import com.virtualcouch.pucci.dev.data.test.SampleData
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import com.virtualcouch.pucci.dev.domain.repository.VideoListResult
import com.virtualcouch.pucci.dev.ui.state.FeedType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SampleVideoDataRepository: VideoDataRepository {
    override fun fetchData(feedType: FeedType, after: String?): Flow<VideoListResult> = 
        flowOf(VideoListResult(SampleData.sampleVideos, null))
}
