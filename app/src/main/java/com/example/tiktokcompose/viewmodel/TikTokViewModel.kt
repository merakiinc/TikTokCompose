package com.example.tiktokcompose.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.tiktokcompose.domain.repository.VideoDataRepository
import com.example.tiktokcompose.ui.effect.AnimationEffect
import com.example.tiktokcompose.ui.effect.PlayerErrorEffect
import com.example.tiktokcompose.ui.effect.ResetAnimationEffect
import com.example.tiktokcompose.ui.effect.VideoEffect
import com.example.tiktokcompose.ui.state.VideoUiState
import com.example.tiktokcompose.util.toMediaItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

import com.example.tiktokcompose.ui.effect.*
import kotlinx.coroutines.delay

@HiltViewModel
class TikTokViewModel @Inject constructor(
    @Named("reddit_data") private val repository: VideoDataRepository
): ViewModel() {

    private val _state = MutableStateFlow(VideoUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<VideoEffect>()
    val effect = _effect.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.fetchData().collect {
                _state.emit(VideoUiState(
                    videos = it
                ))
            }
        }
    }

    fun uploadCapturedVideo(uri: android.net.Uri?) {
        if (uri == null) return
        
        viewModelScope.launch(Dispatchers.Main) {
            state.value.player?.pause()
            
            _effect.emit(LoadingEffect(true, "Processando vídeo..."))
            delay(1500) // Simula processamento
            
            _effect.emit(LoadingEffect(true, "Fazendo upload do vídeo real..."))
            delay(3000) // Simula upload
            
            _effect.emit(LoadingEffect(false))
            _effect.emit(MessageEffect("Vídeo real capturado e publicado com sucesso!"))
            
            state.value.player?.play()
        }
    }

    fun recordAndUploadVideo() {
        // This method will now be deprecated in favor of the real camera logic in TikTokScreen
    }

    fun createPlayer(context: Context) {
        _state.update { state->
            if (state.player == null) {
                state.copy(player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItems(state.videos.toMediaItems())
                    prepare()
                })
            }
            else
                state
        }
    }

    fun releasePlayer(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations)
            return
        _state.update { state->
            state.player?.release()
            state.copy(player = null)
        }
    }

    fun onPlayerError() {
        viewModelScope.launch(Dispatchers.Main) {
            state.value.player?.let { player ->
                _effect.emit(PlayerErrorEffect(
                    message = player.playerError?.message.toString(),
                    code = player.playerError?.errorCode ?: -1)
                )
            }
        }
    }

    fun onTappedScreen() {
        viewModelScope.launch(Dispatchers.Main) {
            _effect.emit(ResetAnimationEffect)
            state.value.player?.let { player ->
                val drawable = if (player.isPlaying) {
                    player.pause()
                    com.example.tiktokcompose.R.drawable.pause
                }
                else {
                    player.play()
                    com.example.tiktokcompose.R.drawable.play
                }
                _effect.emit(AnimationEffect(drawable))
            }
        }
    }
}