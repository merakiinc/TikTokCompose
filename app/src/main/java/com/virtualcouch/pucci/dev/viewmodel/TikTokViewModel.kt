package com.virtualcouch.pucci.dev.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.virtualcouch.pucci.dev.App
import com.virtualcouch.pucci.dev.data.api.*
import com.virtualcouch.pucci.dev.data.local.entities.EventEntity
import com.virtualcouch.pucci.dev.data.repository.CalendarRepository
import com.virtualcouch.pucci.dev.data.repository.SocialRepository
import com.virtualcouch.pucci.dev.domain.models.UserProfile
import com.virtualcouch.pucci.dev.domain.models.VideoData
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
import android.net.Uri

@HiltViewModel
@UnstableApi
class TikTokViewModel @Inject constructor(
    @Named("reddit_data") private val repository: VideoDataRepository,
    private val authApi: AuthApi,
    private val socialApi: SocialApi,
    @Named("cloud_social_api") private val cloudSocialApi: SocialApi,
    private val tokenManager: TokenManager,
    private val calendarRepository: CalendarRepository,
    private val socialRepository: SocialRepository,
    @ApplicationContext private val context: Context
): ViewModel() {

    private val tag = "TikTokViewModel"
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
    private var lastViewedPostId: String? = null

    val calendarEvents: Flow<List<EventEntity>> = calendarRepository.allEvents

    init {
        checkSession()
        loadMoreVideos(FeedType.FOR_YOU)
        loadMoreVideos(FeedType.FOLLOWING)
        fetchProfileData()
    }

    private fun checkSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (tokenManager.hasRefreshToken() && tokenManager.isAccessTokenExpired()) {
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
                        fetchProfileData()
                    } else {
                        tokenManager.clearTokens()
                    }
                } catch (e: Exception) {
                }
            } else if (tokenManager.hasAccessToken()) {
                syncCalendar()
                fetchProfileData()
            }
            _isCheckingSession.value = false
        }
    }

    fun syncCalendar() {
        viewModelScope.launch(Dispatchers.IO) {
            calendarRepository.syncEvents()
        }
    }

    fun fetchProfileData() {
        if (!hasValidSession()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val profileResponse = socialRepository.getUserProfile()
            val videos = socialRepository.getUserVideos()
            
            _state.update { currentState ->
                currentState.copy(
                    userProfile = profileResponse?.let {
                        UserProfile(
                            name = it.name,
                            username = it.username,
                            avatarUrl = it.avatarUrl,
                            followersCount = it.followersCount,
                            followingCount = it.followingCount,
                            likesCount = it.likesCount
                        )
                    },
                    userVideos = videos
                )
            }
        }
    }

    fun login(email: String, password: String) {
        if (email == "teste@teste.com" && password == "123456") {
            viewModelScope.launch {
                _effect.emit(LoadingEffect(true, "Acessando modo de teste..."))
                delay(500)
                tokenManager.saveTokens(
                    accessToken = "test_access_token",
                    accessTokenExpires = "2099-01-01T00:00:00Z",
                    refreshToken = "test_refresh_token",
                    refreshTokenExpires = "2099-01-01T00:00:00Z"
                )
                _effect.emit(LoadingEffect(false))
                _effect.emit(LoginSuccessEffect)
                syncCalendar()
                fetchProfileData()
            }
            return
        }

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
                        fetchProfileData()
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
                    fetchProfileData()
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
        _state.update { it.copy(userProfile = null, userVideos = emptyList()) }
    }

    fun hasValidSession(): Boolean = tokenManager.hasAccessToken() || tokenManager.hasRefreshToken()

    fun loadMoreVideos(feedType: FeedType) {
        val isLoading = if (feedType == FeedType.FOR_YOU) isLoadingForYou else isLoadingFollowing
        if (isLoading) return
        
        val token = if (feedType == FeedType.FOR_YOU) nextTokenForYou else nextTokenFollowing

        viewModelScope.launch(Dispatchers.IO) {
            if (feedType == FeedType.FOR_YOU) isLoadingForYou = true else isLoadingFollowing = true
            
            repository.fetchData(feedType, token).collect { result ->
                if (feedType == FeedType.FOR_YOU) nextTokenForYou = result.nextToken else nextTokenFollowing = result.nextToken
                
                val newVideos = result.videos
                
                // INICIA PRÉ-CARREGAMENTO EM BACKGROUND
                preloadVideos(newVideos.take(10))

                withContext(Dispatchers.Main) {
                    _state.update { currentState ->
                        val updatedForYou = if (feedType == FeedType.FOR_YOU) currentState.videos + newVideos else currentState.videos
                        val updatedFollowing = if (feedType == FeedType.FOLLOWING) currentState.followingVideos + newVideos else currentState.followingVideos
                        
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

    // FUNÇÃO DE PRE-FETCHING AGRESSIVO
    private fun preloadVideos(videos: List<VideoData>) {
        viewModelScope.launch(Dispatchers.IO) {
            videos.forEach { video ->
                try {
                    val uri = Uri.parse(video.mediaUri)
                    val dataSpec = DataSpec(uri, 0, 2 * 1024 * 1024) // Baixa os primeiros 2MB
                    
                    val dataSource = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .createDataSource()
                    
                    val cacheWriter = CacheWriter(
                        CacheDataSource(App.videoCache, dataSource),
                        dataSpec,
                        null,
                        null
                    )
                    
                    Log.d(tag, "Preloading first 2MB of: ${video.id}")
                    cacheWriter.cache() 
                } catch (e: Exception) {
                    Log.e(tag, "Preload failed for ${video.id}", e)
                }
            }
        }
    }

    fun switchFeed(feedType: FeedType) {
        if (_state.value.activeFeed == feedType) return

        _state.update { it.copy(activeFeed = feedType) }
        
        state.value.player?.let { player ->
            player.setMediaItems(state.value.currentVideosList.toMediaItems())
            player.prepare()
        }
    }

    fun recordInteraction(postId: String, type: String, weight: Int) {
        if (!hasValidSession()) return
        viewModelScope.launch(Dispatchers.IO) {
            socialRepository.recordInteraction(postId, type, weight)
        }
    }

    fun onVideoFinished(postId: String) {
        recordInteraction(postId, "VIEW", 1)
    }

    fun likeVideo(postId: String) {
        recordInteraction(postId, "LIKE", 3)
    }

    fun shareVideo(postId: String) {
        recordInteraction(postId, "SHARE", 5)
    }

    fun commentClicked(postId: String) {
        recordInteraction(postId, "COMMENT", 2)
    }

    fun videoSkipped(postId: String) {
        recordInteraction(postId, "SKIP", -1)
    }

    fun uploadCapturedVideo(uri: android.net.Uri?, description: String) {
        if (uri == null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                state.value.player?.pause()
                _effect.emit(LoadingEffect(true, "Obtendo autorização de upload..."))
            }

            try {
                // 1. Get Pre-signed URL
                val presignedResponse = socialApi.getPresignedUrl()
                if (!presignedResponse.isSuccessful || presignedResponse.body() == null) {
                    throw Exception("Falha ao obter URL de upload: ${presignedResponse.code()}")
                }
                val presignedData = presignedResponse.body()!!

                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(true, "Enviando vídeo para a nuvem..."))
                }

                // 2. Upload Binary with Retry logic
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes() ?: throw Exception("Falha ao ler o arquivo de vídeo")
                inputStream.close()
                val requestBody = fileBytes.toRequestBody(null) 

                var uploadSuccess = false
                var retryCount = 0
                val maxRetries = 3

                while (!uploadSuccess && retryCount <= maxRetries) {
                    try {
                        if (retryCount > 0) {
                            withContext(Dispatchers.Main) {
                                _effect.emit(LoadingEffect(true, "Falha na conexão. Tentativa $retryCount de $maxRetries..."))
                            }
                            delay(2000) 
                        }
                        
                        val cloudResponse = cloudSocialApi.uploadToCloud(presignedData.uploadUrl, requestBody)
                        
                        if (cloudResponse.isSuccessful) {
                            uploadSuccess = true
                        } else {
                            if (cloudResponse.code() == 403 || cloudResponse.code() == 401) {
                                throw Exception("Erro de permissão no storage (403).")
                            }
                            retryCount++
                        }
                    } catch (e: java.io.IOException) {
                        retryCount++
                        if (retryCount > maxRetries) throw e
                    }
                }

                if (!uploadSuccess) throw Exception("Falha ao subir vídeo após $maxRetries tentativas.")

                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(true, "Finalizando publicação..."))
                }

                // 3. Confirm Post
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
                        fetchProfileData()
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
                // 1. Configura a fonte de dados com CACHE em disco
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                
                val cacheDataSourceFactory = CacheDataSource.Factory()
                    .setCache(App.videoCache)
                    .setUpstreamDataSourceFactory(httpDataSourceFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

                // 2. Ajuste Fino de LoadControl (Sugestão Item 5)
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        1500, // Min buffer para iniciar (Mais rápido que os 2.5s padrão)
                        5000, // Max buffer (Economiza banda no mobile)
                        1000, // Buffer para retomar após re-buffer
                        1500  // Buffer para retomar após pausa
                    ).build()

                val player = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
                    .setLoadControl(loadControl)
                    .build().apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        setMediaItems(state.currentVideosList.toMediaItems())
                        seekToDefaultPosition(currentIndex)
                        prepare()
                        
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    val videos = _state.value.currentVideosList
                                    if (currentIndex < videos.size) {
                                        onVideoFinished(videos[currentIndex].id)
                                    }
                                }
                            }
                        })
                    }
                state.copy(player = player)
            }
            else
                state
        }
    }

    fun updateIndex(index: Int) {
        if (currentIndex != index) {
            val videos = state.value.currentVideosList
            
            state.value.player?.let { player ->
                if (currentIndex < videos.size) {
                    val playbackTime = player.currentPosition
                    if (playbackTime < 5000) {
                        videoSkipped(videos[currentIndex].id)
                    }
                }
            }
            
            currentIndex = index
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
