package com.virtualcouch.pucci.dev.ui.composables

import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.ui.effect.*
import com.virtualcouch.pucci.dev.ui.state.FeedType
import com.virtualcouch.pucci.dev.ui.state.VideoUiState
import com.virtualcouch.pucci.dev.util.findActivity
import com.virtualcouch.pucci.dev.util.showToast
import com.virtualcouch.pucci.dev.viewmodel.TikTokViewModel
import java.io.File
import android.net.Uri
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class)
@Composable
fun VirtualCouchScreen(
    modifier: Modifier = Modifier,
    viewModel: TikTokViewModel = hiltViewModel(),
    onLogout: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    // Gerenciamento de Permissões
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )
    
    // Preparação para captura de vídeo
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            viewModel.uploadCapturedVideo(videoUri)
        }
    }

    val mainPagerState = rememberPagerState(initialPage = 1) { 2 } // 0: Following, 1: For You

    LaunchedEffect(mainPagerState.currentPage) {
        val newFeed = if (mainPagerState.currentPage == 0) FeedType.FOLLOWING else FeedType.FOR_YOU
        viewModel.switchFeed(newFeed)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = mainPagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val feedType = if (page == 0) FeedType.FOLLOWING else FeedType.FOR_YOU
            VideoPager(
                state = state,
                feedType = feedType,
                viewModel = viewModel
            )
        }

        VirtualCouchTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            activeFeed = state.activeFeed,
            onLogout = onLogout,
            onFeedClick = { feed ->
                val page = if (feed == FeedType.FOLLOWING) 0 else 1
                scope.launch {
                    mainPagerState.animateScrollToPage(page)
                }
            }
        )
        
        VirtualCouchBottomNavigation(
            modifier = Modifier.align(Alignment.BottomCenter),
            onAddClick = {
                if (permissionState.allPermissionsGranted) {
                    try {
                        val videoFile = File(context.cacheDir, "captured_video_${System.currentTimeMillis()}.mp4")
                        if (!videoFile.parentFile.exists()) videoFile.parentFile.mkdirs()
                        
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            videoFile
                        )
                        videoUri = uri
                        cameraLauncher.launch(uri)
                    } catch (e: Exception) {
                        showToast(context, "Erro ao preparar câmera: ${e.message}")
                    }
                } else {
                    permissionState.launchMultiplePermissionRequest()
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = loadingMessage, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LaunchedEffect(key1 = true) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LoadingEffect -> {
                    isLoading = effect.isLoading
                    loadingMessage = effect.message
                }
                is MessageEffect -> {
                    showToast(context, effect.message)
                }
                // ... (rest of effects handled below in Player)
                else -> {}
            }
        }
    }
}

@Composable
fun VirtualCouchTopBar(
    modifier: Modifier = Modifier,
    activeFeed: FeedType,
    onLogout: () -> Unit = {},
    onFeedClick: (FeedType) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedTabText(
                text = "Seguindo",
                isActive = activeFeed == FeedType.FOLLOWING,
                onClick = { onFeedClick(FeedType.FOLLOWING) }
            )
            Spacer(modifier = Modifier.width(20.dp))
            FeedTabText(
                text = "Virtual Couch",
                isActive = activeFeed == FeedType.FOR_YOU,
                onClick = { onFeedClick(FeedType.FOR_YOU) }
            )
        }
        
        IconButton(
            onClick = onLogout,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp, top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                tint = Color.White
            )
        }
    }
}

@Composable
fun FeedTabText(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Text(
            text = text,
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .background(Color.White)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPager(
    state: VideoUiState,
    feedType: FeedType,
    viewModel: TikTokViewModel = hiltViewModel()
) {
    val videos = if (feedType == FeedType.FOR_YOU) state.videos else state.followingVideos
    val pagerState = rememberPagerState { videos.size }

    // Logic to load more videos when reaching the end
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= videos.size - 5 && videos.isNotEmpty()) {
            viewModel.loadMoreVideos(feedType)
        }
    }

    // Sync ViewModel with current pager index
    LaunchedEffect(pagerState.currentPage) {
        viewModel.updateIndex(pagerState.currentPage)
    }

    // Force play when state.player is available or app resumes
    LaunchedEffect(state.player, pagerState.currentPage, state.activeFeed) {
        if (state.activeFeed == feedType) {
            state.playMediaAt(pagerState.currentPage)
        }
    }

    VerticalPager(
        state = pagerState,
        horizontalAlignment = Alignment.CenterHorizontally,
        key = { index ->
            if (index < videos.size) videos[index].id else index
        }
    ) { index ->
        if (index == pagerState.currentPage && state.activeFeed == feedType) {
            VideoCard(
                player = state.player,
                video = videos[index],
                viewModel = viewModel
            )
        }
        else {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoThumbnail(video = videos[index])
            }
        }
    }
}

@Composable
fun VideoCard(
    player: Player?,
    video: VideoData,
    viewModel: TikTokViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showPlayer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Create player view linked to the player instance
    val playerView = player?.let { rememberPlayerView(it) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.createPlayer(context)
            Lifecycle.Event.ON_STOP -> {
                showPlayer = false // IMPORTANTE: Reseta para thumbnail ao sair
                viewModel.releasePlayer(context.findActivity()?.isChangingConfigurations == true)
            }
            Lifecycle.Event.ON_RESUME -> {
                scope.launch {
                    delay(200) // Pequeno fôlego para o sistema renderizar a view
                    viewModel.play()
                }
            }
            Lifecycle.Event.ON_PAUSE -> viewModel.pause()
            else -> {}
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (player != null && playerView != null) {
            PlayerListener(
                player = player
            ) { event->
                when (event) {
                    Player.EVENT_RENDERED_FIRST_FRAME -> {
                        showPlayer = true
                    }
                    Player.EVENT_PLAYER_ERROR -> {
                        viewModel.onPlayerError()
                    }
                }
            }
            Player(
                playerView = playerView,
                viewModel = viewModel,
                aspectRatio = video.aspectRatio ?: 1f
            )
        }
        
        // Exibe thumbnail enquanto o player não renderizou o frame
        if (!showPlayer) {
            VideoThumbnail(video = video)
        }
    }
}

@Composable
fun BoxScope.VideoThumbnail(
    video: VideoData
) {
    Image(
        painter = rememberAsyncImagePainter(
            model = video.previewImageUri
        ),
        contentDescription = "Preview",
        modifier = Modifier
            .aspectRatio(video.aspectRatio ?: 1f)
            .align(Alignment.Center)
            .fillMaxSize()
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Player(
    playerView: PlayerView,
    viewModel: TikTokViewModel,
    modifier: Modifier = Modifier,
    aspectRatio: Float
) {
    var animatedIconDrawable by remember {
        mutableStateOf(0)
    }
    val iconVisibleState = remember {
        MutableTransitionState(false)
    }
    var animationJob: Job? by remember {
        mutableStateOf(null)
    }
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.onTappedScreen()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .align(Alignment.Center),
            update = { view ->
                // Nudge para forçar o redesenho se necessário
                view.invalidate()
            }
        )
        AnimatedVisibility(
            visibleState = iconVisibleState,
            enter = scaleIn(
                spring(Spring.DampingRatioMediumBouncy)
            ),
            exit = scaleOut(tween(150)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                painter = painterResource(animatedIconDrawable),
                contentDescription = null,
                tint = Color.White.copy(0.90f),
                modifier = Modifier
                    .size(100.dp)
            )
        }
    }
    LaunchedEffect(key1 = true) {
        viewModel.effect.collect { effect->
            when (effect) {
                is PlayerErrorEffect -> {
                    val message = if (effect.code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || effect.code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                        "Please check your internet connection"
                    else
                        "An error occurred. Code: ${effect.code}"
                    showToast(context, message)
                }
                is AnimationEffect -> {
                    animatedIconDrawable = effect.drawable
                    animationJob?.cancel()
                    animationJob = launch {
                        iconVisibleState.targetState = true
                        delay(800)
                        iconVisibleState.targetState = false
                    }
                }
                is ResetAnimationEffect -> {
                    iconVisibleState.targetState = false
                    animationJob?.cancel()
                }
                else -> {}
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun rememberPlayerView(player: Player): PlayerView {
    val context = LocalContext.current
    val playerView = remember(player) {
        PlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT)
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            this.player = player
        }
    }
    
    DisposableEffect(key1 = player) {
        onDispose {
            playerView.player = null
        }
    }
    return playerView
}
