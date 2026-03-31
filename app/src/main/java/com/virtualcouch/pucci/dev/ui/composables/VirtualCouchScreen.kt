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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import com.virtualcouch.pucci.dev.domain.models.UserProfile
import com.virtualcouch.pucci.dev.domain.models.VideoData
import com.virtualcouch.pucci.dev.ui.effect.*
import com.virtualcouch.pucci.dev.ui.state.FeedType
import com.virtualcouch.pucci.dev.ui.state.VideoUiState
import com.virtualcouch.pucci.dev.util.findActivity
import com.virtualcouch.pucci.dev.util.showToast
import com.virtualcouch.pucci.dev.viewmodel.TikTokViewModel
import java.io.File
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

    var isTransitioningToProfile by remember { mutableStateOf(false) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> viewModel.createPlayer(context)
            Lifecycle.Event.ON_STOP -> viewModel.releasePlayer(context.findActivity()?.isChangingConfigurations == true)
            else -> {}
        }
    }

    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && pendingVideoUri != null) showDescriptionDialog = true
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
        } else if (mainPagerState.currentPage == 2) {
            // Lazy Load: Busca o perfil do dono do vídeo atual
            val currentVideo = state.currentVideosList.getOrNull(state.player?.currentMediaItemIndex ?: -1)
            currentVideo?.let { video ->
                viewModel.fetchAuthorProfile(video.authorId) 
            }
        }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        sheetBackgroundColor = Color(0xFF1A1A1A),
        sheetContent = { CommentsSheetContent() }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            backgroundColor = Color.Black,
            bottomBar = {
                VirtualCouchBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (route == "profile") {
                            viewModel.fetchProfileData()
                        }
                        onNavigate(route)
                    },
                    onAddClick = {
                        if (permissionState.allPermissionsGranted) {
                            val videoFile = File(context.cacheDir, "cap_${System.currentTimeMillis()}.mp4")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
                            pendingVideoUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            permissionState.launchMultiplePermissionRequest()
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (currentRoute) {
                    "main" -> {
                        HorizontalPager(
                            state = mainPagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (page) {
                                0 -> VideoPager(state = state, feedType = FeedType.FOLLOWING, viewModel = viewModel, onCommentsClick = { scope.launch { sheetState.show() } })
                                1 -> VideoPager(state = state, feedType = FeedType.FOR_YOU, viewModel = viewModel, onCommentsClick = { scope.launch { sheetState.show() } })
                                2 -> AuthorProfileScreen(profile = state.authorProfile, videos = state.authorVideos)
                            }
                        }

                        if (mainPagerState.currentPage < 2) {
                            VirtualCouchTopBar(
                                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                                activeFeed = if (mainPagerState.currentPage == 0) FeedType.FOLLOWING else FeedType.FOR_YOU,
                                onFeedClick = { feed ->
                                    scope.launch { mainPagerState.animateScrollToPage(if (feed == FeedType.FOLLOWING) 0 else 1) }
                                }
                            )
                        }
                    }
                    "agenda" -> AgendaScreen()
                    "profile" -> ProfileScreen(profile = state.userProfile, videos = state.userVideos, onLogout = onLogout)
                }

                if (showDescriptionDialog) {
                    AlertDialog(
                        onDismissRequest = { showDescriptionDialog = false },
                        title = { Text("Publicar vídeo", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            OutlinedTextField(
                                value = videoDescription,
                                onValueChange = { videoDescription = it },
                                placeholder = { Text("Legenda...", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Color(0xFF1D4EEE))
                            )
                        },
                        confirmButton = {
                            Button(onClick = {
                                showDescriptionDialog = false
                                viewModel.uploadCapturedVideo(pendingVideoUri, videoDescription)
                                videoDescription = "" 
                            }) { Text("Publicar") }
                        },
                        backgroundColor = Color(0xFF1A1A1A)
                    )
                }
            }
        }
    }
}

@Composable
fun AuthorProfileScreen(
    profile: UserProfile?,
    videos: List<VideoData>
) {
    if (profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(top = 16.dp)) {
        // Header
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = rememberAsyncImagePainter(profile.avatarUrl),
                contentDescription = null,
                modifier = Modifier.size(90.dp).clip(CircleShape).border(2.dp, Color.DarkGray, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = profile.username ?: "Psicólogo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Stats
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ProfileStatItem(profile.followingCount, "Seguindo")
                ProfileStatItem(profile.followersCount, "Seguidores")
                ProfileStatItem(profile.likesCount, "Curtidas")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Bio
            profile.bio?.let {
                Text(text = it, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp), fontSize = 14.sp)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Links
            profile.links?.forEach { link ->
                Text(
                    text = link.label,
                    color = Color(0xFF1D4EEE),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                    }.padding(4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { /* Follow Logic */ },
                colors = ButtonDefaults.buttonColors(backgroundColor = if (profile.isFollowing) Color.DarkGray else Color(0xFF1D4EEE)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(160.dp).height(36.dp)
            ) {
                Text(text = if (profile.isFollowing) "Seguindo" else "Seguir", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Videos Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(videos) { video ->
                Box(modifier = Modifier.aspectRatio(3f/4f).background(Color.DarkGray)) {
                    Image(
                        painter = rememberAsyncImagePainter(video.previewImageUri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "❤️ ${video.likes}",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileStatItem(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun CommentsSheetContent() {
    Column(modifier = Modifier.fillMaxWidth().height(500.dp).padding(16.dp)) {
        Text("Comentários", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(10) { index ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Usuário $index", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Ótimo conteúdo!", color = Color.White, fontSize = 14.sp)
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
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedTabText(text = "Seguindo", isActive = activeFeed == FeedType.FOLLOWING, onClick = { onFeedClick(FeedType.FOLLOWING) })
            Spacer(modifier = Modifier.width(20.dp))
            FeedTabText(text = "For You", isActive = activeFeed == FeedType.FOR_YOU, onClick = { onFeedClick(FeedType.FOR_YOU) })
        }
    }
}

@Composable
fun FeedTabText(text: String, isActive: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onClick)) {
        Text(text = text, color = if (isActive) Color.White else Color.White.copy(alpha = 0.6f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        if (isActive) Box(modifier = Modifier.width(30.dp).height(2.dp).background(Color.White))
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
        if (pagerState.currentPage >= videos.size - 5 && videos.isNotEmpty()) viewModel.loadMoreVideos(feedType)
    }

    LaunchedEffect(pagerState.currentPage) { viewModel.updateIndex(pagerState.currentPage) }

    LaunchedEffect(state.player, pagerState.currentPage, state.activeFeed) {
        if (state.activeFeed == feedType) state.playMediaAt(pagerState.currentPage)
    }

    VerticalPager(
        state = pagerState,
        horizontalAlignment = Alignment.CenterHorizontally,
        beyondViewportPageCount = 1,
        key = { index -> if (index < videos.size) videos[index].id else index }
    ) { index ->
        if (index == pagerState.currentPage && state.activeFeed == feedType) {
            VideoCard(player = state.player, video = videos[index], viewModel = viewModel, onCommentsClick = onCommentsClick)
        } else {
            Box(modifier = Modifier.fillMaxSize()) { VideoThumbnail(video = videos[index]) }
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
    val playerView = player?.let { rememberPlayerView(it) }

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> viewModel.play()
            Lifecycle.Event.ON_PAUSE -> viewModel.pause()
            Lifecycle.Event.ON_STOP -> showPlayer = false
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (player != null && playerView != null) {
            PlayerListener(player = player) { event->
                if (event == Player.EVENT_RENDERED_FIRST_FRAME) showPlayer = true
                if (player.playbackState == Player.STATE_READY) showPlayer = true
            }
            Player(playerView = playerView, viewModel = viewModel, aspectRatio = video.aspectRatio ?: 1f, postId = video.id)
        }
        
        if (!showPlayer) VideoThumbnail(video = video)

        Box(modifier = Modifier.fillMaxSize()) {
            VideoSideBar(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 40.dp), 
                video = video,
                onCommentsClick = { viewModel.commentClicked(video.id); onCommentsClick() },
                onLikeClick = { viewModel.likeVideo(video.id) },
                onShareClick = { viewModel.shareVideo(video.id) }
            )
            
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 40.dp)) {
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
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Image(
                painter = rememberAsyncImagePainter(video.authorAvatar),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.offset(y = 10.dp).size(20.dp).clip(CircleShape).background(Color.Red), contentAlignment = Alignment.Center) {
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
fun SideBarIcon(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(35.dp))
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BoxScope.VideoThumbnail(video: VideoData) {
    if (!video.previewImageUri.isNullOrBlank()) {
        Image(painter = rememberAsyncImagePainter(model = video.previewImageUri), contentDescription = "Preview", modifier = Modifier.aspectRatio(video.aspectRatio ?: 1f).align(Alignment.Center).fillMaxSize())
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(30.dp))
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Player(playerView: PlayerView, viewModel: TikTokViewModel, modifier: Modifier = Modifier, aspectRatio: Float, postId: String) {
    var animatedIconDrawable by remember { mutableStateOf(0) }
    val iconVisibleState = remember { MutableTransitionState(false) }
    var animationJob: Job? by remember { mutableStateOf(null) }
    var showLikeHeart by remember { mutableStateOf(false) }
    val heartScale by animateFloatAsState(targetValue = if (showLikeHeart) 1.2f else 0f, animationSpec = spring())
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures(onTap = { viewModel.onTappedScreen() }, onDoubleTap = { showLikeHeart = true; viewModel.likeVideo(postId) })
    }, contentAlignment = Alignment.Center) {
        AndroidView(factory = { playerView }, modifier = Modifier.aspectRatio(aspectRatio).align(Alignment.Center), update = { it.invalidate() })
        if (showLikeHeart) {
            Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(120.dp).scale(heartScale))
            LaunchedEffect(Unit) { delay(500); showLikeHeart = false }
        }
        AnimatedVisibility(visibleState = iconVisibleState, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)), exit = scaleOut(tween(150)), modifier = Modifier.align(Alignment.Center)) {
            Icon(painter = painterResource(animatedIconDrawable), contentDescription = null, tint = Color.White.copy(0.90f), modifier = Modifier.size(100.dp))
        }
    }
    LaunchedEffect(key1 = true) {
        viewModel.effect.collect { effect->
            when (effect) {
                is PlayerErrorEffect -> showToast(context, "An error occurred. Code: ${effect.code}")
                is AnimationEffect -> {
                    animatedIconDrawable = effect.drawable
                    animationJob?.cancel()
                    animationJob = launch { iconVisibleState.targetState = true; delay(800); iconVisibleState.targetState = false }
                }
                is ResetAnimationEffect -> { iconVisibleState.targetState = false; animationJob?.cancel() }
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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            this.player = player
        }
    }
    DisposableEffect(key1 = player) { onDispose { playerView.player = null } }
    return playerView
}
