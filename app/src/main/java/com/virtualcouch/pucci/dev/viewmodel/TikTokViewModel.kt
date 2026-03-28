package com.virtualcouch.pucci.dev.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.virtualcouch.pucci.dev.data.api.*
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity
import com.virtualcouch.pucci.dev.data.repository.CalendarRepository
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import com.virtualcouch.pucci.dev.ui.effect.*
import com.virtualcouch.pucci.dev.ui.state.FeedType
import com.virtualcouch.pucci.dev.ui.state.VideoUiState
import com.virtualcouch.pucci.dev.util.TokenManager
import com.virtualcouch.pucci.dev.util.toMediaItems
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@HiltViewModel
class TikTokViewModel @Inject constructor(
    @Named("reddit_data") private val repository: VideoDataRepository,
    private val authApi: AuthApi,
    private val socialApi: SocialApi,
    @Named("cloud_social_api") private val cloudSocialApi: SocialApi,
    private val tokenManager: TokenManager,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val context: Context
): ViewModel() {

    private val _state = MutableStateFlow(VideoUiState())
    val state = _state.asStateFlow()

    private val _effect = MutableSharedFlow<VideoEffect>()
    val effect = _effect.asSharedFlow()

    private val _isCheckingSession = MutableStateFlow(true)
    val isCheckingSession = _isCheckingSession.asStateFlow()

    private var nextTokenForYou: String? = null
    private var nextTokenFollowing: String? = null
    private var isLoadingForYou = false
    private var isLoadingFollowing = false
    private var currentIndex = 0

    val calendarEvents: Flow<List<EventEntity>> = calendarRepository.allEvents

    init {
        checkSession()
        loadMoreVideos(FeedType.FOR_YOU)
        loadMoreVideos(FeedType.FOLLOWING)
    }

    private fun checkSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (tokenManager.hasRefreshToken() && tokenManager.isAccessTokenExpired()) {
                // Tenta refresh automático se expirado
                try {
                    val refreshResponse = authApi.refreshTokens(RefreshTokenRequest(refreshToken = tokenManager.getRefreshToken()!!))
                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        val tokens = refreshResponse.body()!!
                        tokenManager.saveTokens(
                            accessToken = tokens.access.token,
                            accessTokenExpires = tokens.access.expires,
                            refreshToken = tokens.refresh.token,
                            refreshTokenExpires = tokens.refresh.expires
                        )
                        syncCalendar()
                    } else {
                        tokenManager.clearTokens() // Refresh falhou
                    }
                } catch (e: Exception) {
                    // Erro de rede no refresh, mantemos o que tem e o interceptor tentará depois
                }
            } else if (tokenManager.hasAccessToken()) {
                syncCalendar()
            }
            _isCheckingSession.value = false
        }
    }

    fun syncCalendar() {
        viewModelScope.launch(Dispatchers.IO) {
            calendarRepository.syncEvents()
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _effect.emit(LoadingEffect(true, "Autenticando..."))
            try {
                val response = authApi.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    
                    val phone = loginResponse.phoneNumber ?: loginResponse.user?.phoneNumber
                    val method = loginResponse.method ?: "sms"

                    if (phone != null) {
                        sendOtp(phone, method)
                    } else if (loginResponse.tokens != null) {
                        tokenManager.saveTokens(
                            accessToken = loginResponse.tokens!!.access.token,
                            accessTokenExpires = loginResponse.tokens!!.access.expires,
                            refreshToken = loginResponse.tokens!!.refresh.token,
                            refreshTokenExpires = loginResponse.tokens!!.refresh.expires
                        )
                        _effect.emit(LoadingEffect(false))
                        _effect.emit(LoginSuccessEffect)
                        syncCalendar()
                    } else {
                        _effect.emit(LoadingEffect(false))
                        _effect.emit(MessageEffect("Dados de login incompletos"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        val moshi = Moshi.Builder().build()
                        val adapter = moshi.adapter(BaseErrorResponse::class.java)
                        adapter.fromJson(errorBody ?: "")?.message ?: "Erro desconhecido"
                    } catch (e: Exception) {
                        "Erro no login (${response.code()})"
                    }
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect(errorMessage))
                }
            } catch (e: Exception) {
                _effect.emit(LoadingEffect(false))
                _effect.emit(MessageEffect("Erro de conexão: ${e.message}"))
            }
        }
    }

    private suspend fun sendOtp(phoneNumber: String, method: String) {
        try {
            val response = authApi.sendOtp(SendOtpRequest(phoneNumber = phoneNumber, method = method))
            _effect.emit(LoadingEffect(false))
            if (response.isSuccessful) {
                _effect.emit(NeedOtpEffect(phoneNumber))
            } else {
                _effect.emit(MessageEffect("Erro ao enviar SMS de verificação"))
            }
        } catch (e: Exception) {
            _effect.emit(LoadingEffect(false))
            _effect.emit(MessageEffect("Falha ao solicitar código: ${e.message}"))
        }
    }

    fun verifyOtp(phoneNumber: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _effect.emit(LoadingEffect(true, "Verificando código..."))
            try {
                val response = authApi.verifyOtp(phoneNumber, token)
                if (response.isSuccessful && response.body()?.tokens != null) {
                    val loginResponse = response.body()!!
                    tokenManager.saveTokens(
                        accessToken = loginResponse.tokens!!.access.token,
                        accessTokenExpires = loginResponse.tokens!!.access.expires,
                        refreshToken = loginResponse.tokens!!.refresh.token,
                        refreshTokenExpires = loginResponse.tokens!!.refresh.expires
                    )
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(LoginSuccessEffect)
                    syncCalendar()
                } else {
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect("Código de verificação inválido"))
                }
            } catch (e: Exception) {
                _effect.emit(LoadingEffect(false))
                _effect.emit(MessageEffect("Erro na verificação: ${e.message}"))
            }
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }

    fun hasValidSession(): Boolean = tokenManager.hasAccessToken() || tokenManager.hasRefreshToken()

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

    fun uploadCapturedVideo(uri: android.net.Uri?, description: String) {
        if (uri == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                state.value.player?.pause()
                _effect.emit(LoadingEffect(true, "Obtendo autorização de upload..."))
            }

            try {
                // 1. Get Pre-signed URL (Usa socialApi com AuthInterceptor)
                val presignedResponse = socialApi.getPresignedUrl()
                if (!presignedResponse.isSuccessful || presignedResponse.body() == null) {
                    throw Exception("Falha ao obter URL de upload: ${presignedResponse.code()}")
                }
                val presignedData = presignedResponse.body()!!

                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(true, "Enviando vídeo para a nuvem..."))
                }

                // 2. Upload Binary to R2/S3 (Usa cloudSocialApi SEM AuthInterceptor e SEM Content-Type customizado)
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes() ?: throw Exception("Falha ao ler o arquivo de vídeo")
                inputStream.close()

                // IMPORTANTE: Passamos null no MediaType para não enviar o header Content-Type,
                // já que a assinatura do R2 fornecida só incluiu o header 'host'.
                val requestBody = fileBytes.toRequestBody(null) 
                val cloudResponse = cloudSocialApi.uploadToCloud(presignedData.uploadUrl, requestBody)
                
                if (!cloudResponse.isSuccessful) {
                    throw Exception("Falha no upload binário: ${cloudResponse.code()} - ${cloudResponse.errorBody()?.string()}")
                }

                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(true, "Finalizando publicação..."))
                }

                // 3. Confirm Post to Backend (Usa socialApi com AuthInterceptor)
                val locale = context.resources.configuration.locales.get(0).toLanguageTag()
                val confirmResponse = socialApi.postVideoConfirm(
                    PostVideoRequest(
                        postId = presignedData.postId,
                        videoUrl = presignedData.publicUrl,
                        content = description,
                        locale = locale
                    )
                )

                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(false))
                    if (confirmResponse.isSuccessful) {
                        _effect.emit(MessageEffect("Vídeo publicado com sucesso!"))
                    } else {
                        _effect.emit(MessageEffect("Erro ao confirmar publicação: ${confirmResponse.code()}"))
                    }
                    state.value.player?.play()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect("Erro no processo de upload: ${e.message}"))
                    state.value.player?.play()
                }
            }
        }
    }

    fun createPlayer(context: Context) {
        _state.update { state->
            if (state.player == null) {
                state.copy(player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItems(state.currentVideosList.toMediaItems())
                    seekToDefaultPosition(currentIndex)
                    prepare()
                })
            }
            else
                state
        }
    }

    fun updateIndex(index: Int) {
        currentIndex = index
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

    fun play() {
        state.value.player?.play()
    }

    fun pause() {
        state.value.player?.pause()
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
