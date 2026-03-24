package com.virtualcouch.pucci.dev.ui.state

import androidx.media3.common.Player
import com.virtualcouch.pucci.dev.domain.models.VideoData

enum class FeedType {
    FOR_YOU, FOLLOWING
}

data class VideoUiState(
    val player: Player? = null,
    val videos: List<VideoData> = emptyList(),
    val followingVideos: List<VideoData> = emptyList(),
    val activeFeed: FeedType = FeedType.FOR_YOU
) {
    val currentVideosList: List<VideoData>
        get() = if (activeFeed == FeedType.FOR_YOU) videos else followingVideos

    fun playMediaAt(position: Int) {
        player?.let { player->
            if (player.currentMediaItemIndex == position && player.isPlaying)
                return

            player.seekToDefaultPosition(position)
            player.playWhenReady = true
            player.prepare()
        }
    }
}
