package com.example.tiktokcompose.data.repository

import com.example.tiktokcompose.data.test.SampleData
import com.example.tiktokcompose.domain.models.VideoData
import com.example.tiktokcompose.domain.repository.VideoDataRepository
import com.example.tiktokcompose.domain.repository.VideoListResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SampleVideoDataRepository: VideoDataRepository {
    override fun fetchData(after: String?): Flow<VideoListResult> = 
        flowOf(VideoListResult(SampleData.sampleVideos, null))
}