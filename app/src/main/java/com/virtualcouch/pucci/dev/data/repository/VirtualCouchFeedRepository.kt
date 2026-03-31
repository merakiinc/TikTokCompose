package com.virtualcouch.pucci.dev.data.repository

import android.util.Log
import com.virtualcouch.pucci.dev.data.api.SocialApi
import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import com.virtualcouch.pucci.dev.domain.repository.VideoListResult
import com.virtualcouch.pucci.dev.ui.state.FeedType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class VirtualCouchFeedRepository @Inject constructor(
    private val api: SocialApi
) : VideoDataRepository {

    private val tag = "VirtualCouchFeedRepo"

    override fun fetchData(feedType: FeedType, after: String?): Flow<VideoListResult> = flow {
        try {
            Log.d(tag, "Fetching feed: $feedType, after: $after")
            val response = if (feedType == FeedType.FOR_YOU) {
                api.getForYouFeed(after)
            } else {
                api.getFollowingFeed(after)
            }

            if (response.isSuccessful && response.body() != null) {
                val feedResponse = response.body()!!
                Log.d(tag, "Received ${feedResponse.videos.size} videos from backend")
                
                val videoData = feedResponse.videos.map { video ->
                    val rawUri = video.hlsUrl ?: video.videoUrl ?: video.dashUrl ?: ""
                    val cleanUrl = rawUri.replace("\\s".toRegex(), "")
                    
                    VideoData(
                        id = video.id,
                        authorId = video.author.id, // MAPEAMENTO CORRETO: ID do usuário e não do vídeo
                        mediaUri = cleanUrl,
                        previewImageUri = video.thumbnailUrl, 
                        aspectRatio = video.aspectRatio ?: 0.5625f,
                        authorName = video.author.username ?: video.author.name?.split(" ")?.firstOrNull() ?: "Psicólogo",
                        authorAvatar = video.author.avatarUrl ?: "https://api.dicebear.com/7.x/avataaars/svg?seed=${video.author.id}",
                        description = video.description ?: "",
                        likes = video.stats.likes,
                        comments = video.stats.comments,
                        shares = video.stats.shares,
                        isLiked = video.userStatus.isLiked,
                        isFollowing = video.userStatus.isFollowing
                    )
                }
                emit(VideoListResult(videoData, feedResponse.nextToken))
            } else {
                Log.e(tag, "Feed load failed: ${response.code()}")
                emit(VideoListResult(emptyList(), null))
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Log.e(tag, "Error fetching feed", throwable)
            emit(VideoListResult(emptyList(), null))
        }
    }
}
