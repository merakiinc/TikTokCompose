package com.virtualcouch.pucci.dev.data.repository

import com.virtualcouch.pucci.dev.data.test.SampleData
import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import com.virtualcouch.pucci.dev.domain.repository.VideoListResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SampleVideoDataRepository: VideoDataRepository {
    override fun fetchData(after: String?): Flow<VideoListResult> = 
        flowOf(VideoListResult(SampleData.sampleVideos, null))
}
