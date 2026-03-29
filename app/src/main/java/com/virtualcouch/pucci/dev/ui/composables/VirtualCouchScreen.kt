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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalPermissionsApi::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun VirtualCouchScreen(
    modifier: Modifier = Modifier,
    viewModel: TikTokViewModel = hiltViewModel(),
    currentRoute: String = "main", 
    onNavigate: (String) -> Unit = {}, 
    onLogout: () -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var videoDescription by remember { mutableStateOf("") }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Estado para evitar play durante transição de abas
    var isTransitioningToProfile by remember { mutableStateOf(false) }

    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && pendingVideoUri != null) {
            showDescriptionDialog = true
        }
    }

    val mainPagerState = rememberPagerState(initialPage = 1) { 3 } 

    // Lógica Unificada de Pausa de Áudio
    LaunchedEffect(currentRoute, mainPagerState.currentPage, isTransitioningToProfile) {
        if (currentRoute != "main" || mainPagerState.currentPage == 2 || isTransitioningToProfile) {
            viewModel.pause()
        } else {
            viewModel.play()
        }
    }

    LaunchedEffect(mainPagerState.currentPage) {
        if (mainPagerState.currentPage < 2) {
            val newFeed = if (mainPagerState.currentPage == 0) FeedType.FOLLOWING else FeedType.FOR_YOU
            viewModel.switchFeed(newFeed)
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = Color(0xFF1A1A1A),
        sheetContent = {
            CommentsSheetContent()
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            backgroundColor = Color.Black,
            bottomBar = {
                VirtualCouchBottomNavigation(
                    currentRoute = if (currentRoute == "main" && mainPagerState.currentPage == 2) "profile" else currentRoute,
                    onNavigate = { route ->
                        when (route) {
                            "profile" -> {
                                if (currentRoute != "main") {
                                    isTransitioningToProfile = true
                                    onNavigate("main")
                                }
                                scope.launch {
                                    mainPagerState.scrollToPage(2)
                                    isTransitioningToProfile = false
                                }
                            }
                            "main" -> {
                                onNavigate("main")
                                scope.launch {
                                    mainPagerState.animateScrollToPage(1)
                                }
                            }
                            "agenda" -> {
                                onNavigate("agenda")
                            }
                        }
                    },
                    onAddClick = {
                        if (permissionState.allPermissionsGranted) {
                            try {
                                val videoFile = File(context.cacheDir, "captured_video_${System.currentTimeMillis()}.mp4")
                                videoFile.parentFile?.let {
                                    if (!it.exists()) it.mkdirs()
                                }
                                
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    videoFile
                                )
                                pendingVideoUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                showToast(context, "Erro ao preparar câmera: ${e.message}")
                            }
                        } else {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (currentRoute == "main") {
                    HorizontalPager(
                        state = mainPagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true
                    ) { page ->
                        when (page) {
                            0 -> VideoPager(state = state, feedType = FeedType.FOLLOWING, viewModel = viewModel, onCommentsClick = { scope.launch { sheetState.show() } })
                            1 -> VideoPager(state = state, feedType = FeedType.FOR_YOU, viewModel = viewModel, onCommentsClick = { scope.launch { sheetState.show() } })
                            2 -> ProfileScreen(
                                profile = state.userProfile,
                                videos = state.userVideos,
                                onLogout = onLogout
                            )
                        }                    }

                    if (mainPagerState.currentPage < 2) {
                        VirtualCouchTopBar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding(),
                            activeFeed = if (mainPagerState.currentPage == 0) FeedType.FOLLOWING else FeedType.FOR_YOU,
                            onFeedClick = { feed ->
                                val targetPage = if (feed == FeedType.FOLLOWING) 0 else 1
                                scope.launch {
                                    mainPagerState.animateScrollToPage(targetPage)
                                }
                            }
                        )
                    }
                } else if (currentRoute == "agenda") {
                    AgendaScreen()
                }

                if (showDescriptionDialog) {
                    AlertDialog(
                        onDismissRequest = { showDescriptionDialog = false },
                        title = { Text("Publicar vídeo", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Dê uma legenda ao seu vídeo:", color = Color.LightGray, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = videoDescription,
                                    onValueChange = { videoDescription = it },
                                    placeholder = { Text("O que está acontecendo?", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        textColor = Color.White,
                                        focusedBorderColor = Color(0xFF1D4EEE),
                                        unfocusedBorderColor = Color.DarkGray
                                    )
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDescriptionDialog = false
                                    viewModel.uploadCapturedVideo(pendingVideoUri, videoDescription)
                                    videoDescription = "" 
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF1D4EEE))
                            ) {
                                Text("Publicar", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDescriptionDialog = false }) {
                                Text("Cancelar", color = Color.Gray)
                            }
                        },
                        backgroundColor = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

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
                else -> {}
            }
        }
    }
}

@Composable
fun CommentsSheetContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
            .padding(16.dp)
    ) {
        Text(
            text = "Comentários",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(10) { index ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Usuário $index", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Este vídeo é muito bom! Me ajudou bastante.", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VirtualCouchTopBar(
    modifier: Modifier = Modifier,
    activeFeed: FeedType,
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
                text = "For You",
                isActive = activeFeed == FeedType.FOR_YOU,
                onClick = { onFeedClick(FeedType.FOR_YOU) }
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
    viewModel: TikTokViewModel = hiltViewModel(),
    onCommentsClick: () -> Unit = {}
) {
    val videos = if (feedType == FeedType.FOR_YOU) state.videos else state.followingVideos
    val pagerState = rememberPagerState { videos.size }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= videos.size - 5 && videos.isNotEmpty()) {
            viewModel.loadMoreVideos(feedType)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.updateIndex(pagerState.currentPage)
    }

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
                viewModel = viewModel,
                onCommentsClick = onCommentsClick
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
    viewModel: TikTokViewModel = hiltViewModel(),
    onCommentsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showPlayer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val playerView = player?.let { rememberPlayerView(it) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.createPlayer(context)
            Lifecycle.Event.ON_STOP -> {
                showPlayer = false 
                viewModel.releasePlayer(context.findActivity()?.isChangingConfigurations == true)
            }
            Lifecycle.Event.ON_RESUME -> {
                scope.launch {
                    delay(200) 
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
                aspectRatio = video.aspectRatio ?: 1f,
                postId = video.id
            )
        }
        
        if (!showPlayer) {
            VideoThumbnail(video = video)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            VideoSideBar(
                modifier = Modifier
                    .align(Alignment.BottomEnd) 
                    .padding(end = 8.dp, bottom = 40.dp), 
                video = video,
                onCommentsClick = {
                    viewModel.commentClicked(video.id)
                    onCommentsClick()
                },
                onLikeClick = { viewModel.likeVideo(video.id) },
                onShareClick = { viewModel.shareVideo(video.id) }
            )
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 40.dp) 
            ) {
                Text("@${video.authorName}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(video.description, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun VideoSideBar(
    modifier: Modifier = Modifier,
    video: VideoData,
    onCommentsClick: () -> Unit,
    onLikeClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Image(
                painter = rememberAsyncImagePainter(video.authorAvatar),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .offset(y = 10.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Red),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        SideBarIcon(icon = Icons.Default.Favorite, label = video.likes, tint = if (video.isLiked) Color.Red else Color.White, onClick = onLikeClick)
        SideBarIcon(icon = Icons.AutoMirrored.Filled.Comment, label = video.comments, onClick = onCommentsClick)
        SideBarIcon(icon = Icons.Default.Share, label = video.shares, onClick = onShareClick)
    }
}

@Composable
fun SideBarIcon(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(35.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    aspectRatio: Float,
    postId: String
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
    
    var showLikeHeart by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(targetValue = if (showLikeHeart) { 1.2f } else 0f, animationSpec = spring())

    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.onTappedScreen() },
                    onDoubleTap = {
                        showLikeHeart = true
                        viewModel.likeVideo(postId)
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
                view.invalidate()
            }
        )
        
        if (showLikeHeart) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.Red.copy(alpha = 0.8f),
                modifier = Modifier.size(120.dp).scale(heartScale)
            )
            LaunchedEffect(Unit) {
                delay(500)
                showLikeHeart = false
            }
        }

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
