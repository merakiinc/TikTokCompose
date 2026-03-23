package com.example.tiktokcompose.ui.composables

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Icon
import androidx.compose.material.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.example.tiktokcompose.domain.models.VideoData
import com.example.tiktokcompose.ui.effect.AnimationEffect
import com.example.tiktokcompose.ui.effect.PlayerErrorEffect
import com.example.tiktokcompose.ui.effect.ResetAnimationEffect
import com.example.tiktokcompose.ui.state.VideoUiState
import com.example.tiktokcompose.util.findActivity
import com.example.tiktokcompose.util.showToast
import com.example.tiktokcompose.viewmodel.TikTokViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun TikTokScreen(
    modifier: Modifier = Modifier,
    viewModel: TikTokViewModel = hiltViewModel()
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val state by viewModel.state.collectAsState()
        VideoPager(
            state = state,
            viewModel = viewModel
        )

        TikTokTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )
        
        TikTokBottomNavigation(
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun TikTokTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Seguindo",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Para você",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(30.dp)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPager(
    state: VideoUiState,
    viewModel: TikTokViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState { state.videos.size }

    VerticalPager(
        state = pagerState,
        horizontalAlignment = Alignment.CenterHorizontally,
        key = {
            state.videos[it].id
        }
    ) { index ->
        if (index == pagerState.currentPage) {
            state.playMediaAt(index)
            VideoCard(
                player = state.player,
                video = state.videos[index],
                viewModel = viewModel
            )
        }
        else {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoThumbnail(video = state.videos[index])
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
    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.createPlayer(context)
            Lifecycle.Event.ON_STOP -> viewModel.releasePlayer(context.findActivity()?.isChangingConfigurations == true)
            else -> {}
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        var showPlayer by remember { mutableStateOf(false) }
        if (player != null) {
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
            val playerView = rememberPlayerView(player)
            Player(
                playerView = playerView,
                viewModel = viewModel,
                aspectRatio = video.aspectRatio ?: 1f
            )
        }
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
                .align(Alignment.Center)
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
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun rememberPlayerView(player: Player): PlayerView {
    val context = LocalContext.current
    val playerView = remember {
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
        playerView.player = player
        onDispose {
            playerView.player = null
        }
    }
    return playerView
}