package com.virtualcouch.pucci.dev.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.virtualcouch.pucci.dev.data.api.AuthApi
import com.virtualcouch.pucci.dev.data.api.LoginRequest
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import com.virtualcouch.pucci.dev.ui.effect.AnimationEffect
import com.virtualcouch.pucci.dev.ui.effect.PlayerErrorEffect
import com.virtualcouch.pucci.dev.ui.effect.ResetAnimationEffect
import com.virtualcouch.pucci.dev.ui.effect.VideoEffect
import com.virtualcouch.pucci.dev.ui.state.FeedType
import com.virtualcouch.pucci.dev.ui.state.VideoUiState
import com.virtualcouch.pucci.dev.util.TokenManager
import com.virtualcouch.pucci.dev.util.toMediaItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

import com.virtualcouch.pucci.dev.ui.effect.*
import kotlinx.coroutines.delay

@HiltViewModel
class TikTokViewModel @Inject constructor(
    @Named("reddit_data") private val repository: VideoDataRepository,
    private val authApi: AuthApi,
    private val tokenManager: TokenManager
): ViewModel() {

    private val _state = MutableStateFlow(VideoUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<VideoEffect>()
    val effect = _effect.asSharedFlow()

    private var nextTokenForYou: String? = null
    private var nextTokenFollowing: String? = null
    private var isLoadingForYou = false
    private var isLoadingFollowing = false

    init {
        loadMoreVideos(FeedType.FOR_YOU)
        loadMoreVideos(FeedType.FOLLOWING)
    }

    fun login(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _effect.emit(LoadingEffect(true, "Autenticando..."))
            try {
                val response = authApi.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    tokenManager.saveTokens(
                        accessToken = loginResponse.tokens.access.token,
                        refreshToken = loginResponse.tokens.refresh.token
                    )
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(LoginSuccessEffect)
                } else {
                    val errorBody = response.errorBody()?.string() ?: ""
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect("Erro no login (${response.code()}): ${response.message()} $errorBody"))
                }
            } catch (e: Exception) {
                _effect.emit(LoadingEffect(false))
                _effect.emit(MessageEffect("Erro de conexão: ${e.message}"))
            }
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }

    fun hasValidSession(): Boolean = tokenManager.hasAccessToken()

    fun loadMoreVideos(feedType: FeedType) {
        val isLoading = if (feedType == FeedType.FOR_YOU) isLoadingForYou else isLoadingFollowing
        if (isLoading) return
        
        val token = if (feedType == FeedType.FOR_YOU) nextTokenForYou else nextTokenFollowing

        viewModelScope.launch(Dispatchers.IO) {
            if (feedType == FeedType.FOR_YOU) isLoadingForYou = true else isLoadingFollowing = true
            
            repository.fetchData(token).collect { result ->
                if (feedType == FeedType.FOR_YOU) nextTokenForYou = result.nextToken else nextTokenFollowing = result.nextToken
                
                val newVideos = result.videos
                
                withContext(Dispatchers.Main) {
                    _state.update { currentState ->
                        val updatedForYou = if (feedType == FeedType.FOR_YOU) currentState.videos + newVideos else currentState.videos
                        val updatedFollowing = if (feedType == FeedType.FOLLOWING) currentState.followingVideos + newVideos else currentState.followingVideos
                        
                        // Update player only if it's the active feed
                        if (currentState.activeFeed == feedType) {
                            currentState.player?.addMediaItems(newVideos.toMediaItems())
                        }
                        
                        currentState.copy(
                            videos = updatedForYou,
                            followingVideos = updatedFollowing
                        )
                    }
                }
                if (feedType == FeedType.FOR_YOU) isLoadingForYou = false else isLoadingFollowing = false
            }
        }
    }

    fun switchFeed(feedType: FeedType) {
        if (_state.value.activeFeed == feedType) return

        _state.update { it.copy(activeFeed = feedType) }
        
        // Reset player items for the new feed
        state.value.player?.let { player ->
            player.setMediaItems(state.value.currentVideosList.toMediaItems())
            player.prepare()
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

    fun createPlayer(context: Context) {
        _state.update { state->
            if (state.player == null) {
                state.copy(player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItems(state.currentVideosList.toMediaItems())
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
                    com.virtualcouch.pucci.dev.R.drawable.pause
                }
                else {
                    player.play()
                    com.virtualcouch.pucci.dev.R.drawable.play
                }
                _effect.emit(AnimationEffect(drawable))
            }
        }
    }
}
