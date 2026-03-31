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
        // Inicialização Unificada e Robusta
        viewModelScope.launch {
            Log.d(tag, "Startup: Initializing application flow...")
            
            // 1. Primeiro garante que o token está atualizado (se houver um)
            checkSessionTask() 
            
            // 2. Se temos uma sessão (ou refresh funcionou), carrega o feed e o perfil base
            if (hasValidSession()) {
                Log.d(tag, "Startup: Session valid. Loading initial data...")
                // Carrega o perfil do próprio usuário para saber o ID (necessário para o botão Seguir)
                fetchProfileData()
                // Chamamos a versão suspend interna para esperar a conclusão
                loadVideosInternal(FeedType.FOR_YOU)
            } else {
                Log.w(tag, "Startup: No valid session. Redirecting to Login.")
            }
            
            // 3. Só agora liberamos a Splash Screen
            Log.d(tag, "Startup: Initialization complete. Removing Splash.")
            _isCheckingSession.value = false
        }
    }

    private suspend fun checkSessionTask() {
        withContext(Dispatchers.IO) {
            val hasToken = tokenManager.getAccessToken() != null
            val isExpired = tokenManager.isAccessTokenExpired()
            val hasRefresh = tokenManager.hasRefreshToken()

            if (hasRefresh && (isExpired || !hasToken)) {
                Log.d(tag, "Startup: Access token expired or missing. Attempting refresh...")
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
                        Log.i(tag, "Startup: Refresh successful.")
                    } else {
                        Log.e(tag, "Startup: Refresh failed (${refreshResponse.code()}).")
                        // Se o refresh deu 401, a conta realmente deslogou
                        if (refreshResponse.code() == 401) {
                            tokenManager.clearTokens()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Startup: Network error during refresh", e)
                }
            }
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
            
            // Tenta pegar o ID da resposta ou extrai do Token JWT
            val idFromToken = tokenManager.getUserId()

            _state.update { currentState ->
                currentState.copy(
                    userProfile = profileResponse?.let {
                        UserProfile(
                            id = it.id ?: idFromToken, // Fallback para o ID do Token
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

    fun fetchAuthorProfile(authorId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(authorProfile = null, authorVideos = emptyList()) }
            val result = socialRepository.getAuthorProfile(authorId)
            _state.update { currentState ->
                currentState.copy(
                    authorProfile = result?.first,
                    authorVideos = result?.second ?: emptyList()
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
                loadMoreVideos(FeedType.FOR_YOU)
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
                        loadMoreVideos(FeedType.FOR_YOU)
                    } else {
                        _effect.emit(LoadingEffect(false))
                        _effect.emit(MessageEffect("Dados de login incompletos"))
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect("Erro no login (${response.code()})"))
                }
            } catch (e: Exception) {
                _effect.emit(LoadingEffect(false))
                _effect.emit(MessageEffect("Erro de conexão"))
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
                _effect.emit(MessageEffect("Erro ao enviar código"))
            }
        } catch (e: Exception) {
            _effect.emit(LoadingEffect(false))
            _effect.emit(MessageEffect("Falha na solicitação"))
        }
    }

    fun verifyOtp(phoneNumber: String, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _effect.emit(LoadingEffect(true, "Verificando..."))
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
                    loadMoreVideos(FeedType.FOR_YOU)
                } else {
                    _effect.emit(LoadingEffect(false))
                    _effect.emit(MessageEffect("Código inválido"))
                }
            } catch (e: Exception) {
                _effect.emit(LoadingEffect(false))
                _effect.emit(MessageEffect("Erro na verificação"))
            }
        }
    }

    fun logout() {
        tokenManager.clearTokens()
        _state.update { it.copy(userProfile = null, userVideos = emptyList(), videos = emptyList()) }
    }

    fun hasValidSession(): Boolean = tokenManager.hasAccessToken() || tokenManager.hasRefreshToken()

    fun loadMoreVideos(feedType: FeedType) {
        viewModelScope.launch {
            loadVideosInternal(feedType)
        }
    }

    private suspend fun loadVideosInternal(feedType: FeedType) {
        val isLoading = if (feedType == FeedType.FOR_YOU) isLoadingForYou else isLoadingFollowing
        if (isLoading) return
        
        val token = if (feedType == FeedType.FOR_YOU) nextTokenForYou else nextTokenFollowing

        withContext(Dispatchers.IO) {
            if (feedType == FeedType.FOR_YOU) isLoadingForYou = true else isLoadingFollowing = true
            
            try {
                repository.fetchData(feedType, token).collect { result ->
                    if (feedType == FeedType.FOR_YOU) nextTokenForYou = result.nextToken else nextTokenFollowing = result.nextToken
                    
                    val newVideos = result.videos
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
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading videos", e)
            } finally {
                if (feedType == FeedType.FOR_YOU) isLoadingForYou = false else isLoadingFollowing = false
            }
        }
    }

    private fun preloadVideos(videos: List<VideoData>) {
        viewModelScope.launch(Dispatchers.IO) {
            videos.forEach { video ->
                try {
                    val uri = Uri.parse(video.mediaUri)
                    val dataSpec = DataSpec(uri, 0, 2 * 1024 * 1024)
                    val dataSource = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).createDataSource()
                    val cacheWriter = CacheWriter(CacheDataSource(App.videoCache, dataSource), dataSpec, null, null)
                    cacheWriter.cache() 
                } catch (e: Exception) {}
            }
        }
    }

    fun switchFeed(feedType: FeedType) {
        if (_state.value.activeFeed == feedType) return
        _state.update { it.copy(activeFeed = feedType) }
        val targetList = if (feedType == FeedType.FOR_YOU) _state.value.videos else _state.value.followingVideos
        if (targetList.isEmpty()) {
            loadMoreVideos(feedType)
        }
        state.value.player?.let { player ->
            player.setMediaItems(state.value.currentVideosList.toMediaItems())
            player.prepare()
        }
    }

    fun recordInteraction(postId: String, type: String, weight: Int) {
        if (!hasValidSession()) return
        viewModelScope.launch(Dispatchers.IO) { socialRepository.recordInteraction(postId, type, weight) }
    }

    fun onVideoFinished(postId: String) { recordInteraction(postId, "VIEW", 1) }
    fun likeVideo(postId: String) { recordInteraction(postId, "LIKE", 3) }
    fun shareVideo(postId: String) { recordInteraction(postId, "SHARE", 5) }
    fun commentClicked(postId: String) { recordInteraction(postId, "COMMENT", 2) }
    fun videoSkipped(postId: String) { recordInteraction(postId, "SKIP", -1) }

    fun uploadCapturedVideo(uri: android.net.Uri?, description: String) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                state.value.player?.pause()
                _effect.emit(LoadingEffect(true, "Enviando..."))
            }
            try {
                val presignedResponse = socialApi.getPresignedUrl()
                val presignedData = presignedResponse.body()!!
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileBytes = inputStream?.readBytes()!!
                inputStream.close()
                cloudSocialApi.uploadToCloud(presignedData.uploadUrl, fileBytes.toRequestBody(null))
                socialApi.postVideoConfirm(PostVideoRequest(presignedData.postId, presignedData.publicUrl, description, "pt-BR"))
                withContext(Dispatchers.Main) {
                    _effect.emit(LoadingEffect(false))
                    fetchProfileData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { _effect.emit(LoadingEffect(false)) }
            }
        }
    }

    fun createPlayer(context: Context) {
        _state.update { state->
            if (state.player == null) {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                val cacheDataSourceFactory = CacheDataSource.Factory().setCache(App.videoCache).setUpstreamDataSourceFactory(httpDataSourceFactory).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(1500, 5000, 1000, 1500).build()
                val player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory)).setLoadControl(loadControl).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItems(state.currentVideosList.toMediaItems())
                    seekToDefaultPosition(currentIndex)
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                val videos = _state.value.currentVideosList
                                if (currentIndex < videos.size) onVideoFinished(videos[currentIndex].id)
                            }
                        }
                    })
                }
                state.copy(player = player)
            } else state
        }
    }

    fun updateIndex(index: Int) {
        if (currentIndex != index) {
            val videos = state.value.currentVideosList
            state.value.player?.let { player ->
                if (currentIndex < videos.size && player.currentPosition < 5000) videoSkipped(videos[currentIndex].id)
            }
            currentIndex = index
        }
    }

    fun releasePlayer(isChangingConfigurations: Boolean) {
        if (isChangingConfigurations) return
        _state.update { it.player?.release(); it.copy(player = null) }
    }

    fun onPlayerError() {}
    fun play() { state.value.player?.play() }
    fun pause() { state.value.player?.pause() }

    fun onTappedScreen() {
        viewModelScope.launch(Dispatchers.Main) {
            _effect.emit(ResetAnimationEffect)
            state.value.player?.let { player ->
                val drawable = if (player.isPlaying) { player.pause(); com.virtualcouch.pucci.dev.R.drawable.pause }
                else { player.play(); com.virtualcouch.pucci.dev.R.drawable.play }
                _effect.emit(AnimationEffect(drawable))
            }
        }
    }
}
