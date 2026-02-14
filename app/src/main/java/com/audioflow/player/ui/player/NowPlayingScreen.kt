package com.audioflow.player.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.audioflow.player.model.RepeatMode
import com.audioflow.player.model.TrackSource
import com.audioflow.player.ui.theme.*
import kotlin.math.absoluteValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

@kotlin.OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLyrics: (() -> Unit)? = null,
    onNavigateToSearch: ((String) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val showPlaylistSheet by viewModel.showPlaylistSheet.collectAsState()
    val showNewPlaylistDialog by viewModel.showNewPlaylistDialog.collectAsState()
    val showNewFolderDialog by viewModel.showNewFolderDialog.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val showOptionsSheet by viewModel.showOptionsSheet.collectAsState()
    val lyricsEnabled by viewModel.lyricsEnabled.collectAsState()
    val track = playbackState.currentTrack
    
    // Video state
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val videoStreamInfo by viewModel.videoStreamInfo.collectAsState()
    val isVideoLoading by viewModel.isVideoLoading.collectAsState()
    
    // Orientation detection for fullscreen video
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current as? Activity
    
    // Check if current track is liked
    val isLiked = track?.id?.let { likedSongIds.contains(it) } ?: false
    
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var showLyricsScreen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
    // Observe navigation events for Go to Artist/Album
    val navigateToSearch by viewModel.navigateToSearch.collectAsState()
    
    LaunchedEffect(navigateToSearch) {
        navigateToSearch?.let { query ->
            onNavigateToSearch?.invoke(query)
            viewModel.clearNavigateToSearch()
        }
    }
    
    // Swipe gesture state for debouncing
    var isSwipeProcessing by remember { mutableStateOf(false) }
    var accumulatedDragX by remember { mutableStateOf(0f) }
    var accumulatedDragY by remember { mutableStateOf(0f) }
    
    val progress by animateFloatAsState(
        targetValue = sliderPosition ?: playbackState.progress,
        animationSpec = tween(durationMillis = 100),
        label = "progress"
    )
    
    // Scrollable content for lyrics section
    val scrollState = rememberScrollState()
    
    // Reset swipe processing when track changes (enables swiping again)
    LaunchedEffect(track?.id) {
        isSwipeProcessing = false
    }

    // Full screen lyrics
    if (showLyricsScreen) {
        LyricsScreen(
            title = track?.title ?: "Unknown",
            artist = track?.artist ?: "Unknown",
            lyrics = lyrics,
            currentPosition = playbackState.currentPosition,
            duration = playbackState.duration,
            isPlaying = playbackState.isPlaying,
            progress = progress,
            onNavigateBack = { showLyricsScreen = false },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSeek = { viewModel.seekToProgress(it) },
            onShareClick = { /* TODO: Share lyrics */ }
        )
        return
    }


    // Dynamic background color extraction from current track's artwork
    val dynamicBgColor = rememberDynamicBackgroundColor(
        artworkUri = track?.artworkUri
    )

    // ===================== LANDSCAPE FULLSCREEN VIDEO =====================
    if (isVideoMode && isLandscape && videoStreamInfo != null) {
        // Back button exits fullscreen (returns to portrait), not closing the player
        androidx.activity.compose.BackHandler {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // Auto-hide controls state (YouTube-style)
        var controlsVisible by remember { mutableStateOf(true) }
        var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
        
        // Auto-hide after 3 seconds of no interaction
        LaunchedEffect(lastInteraction, controlsVisible) {
            if (controlsVisible) {
                kotlinx.coroutines.delay(3000)
                controlsVisible = false
            }
        }

        // Fullscreen video — tap to toggle controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures {
                        controlsVisible = !controlsVisible
                        lastInteraction = System.currentTimeMillis()
                    }
                }
        ) {
            VideoPlayerView(
                videoUrl = videoStreamInfo!!.videoStreamUrl,
                currentPosition = playbackState.currentPosition,
                isPlaying = playbackState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )
            
            // Animated visibility for all controls
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                exit = androidx.compose.animation.fadeOut(animationSpec = tween(200)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Exit fullscreen button (top-left)
                    IconButton(
                        onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Exit fullscreen",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    // Center play/pause button (large, YouTube-style)
                    IconButton(
                        onClick = {
                            viewModel.togglePlayPause()
                            lastInteraction = System.currentTimeMillis()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    
                    // Bottom timeline (YouTube-style, at very bottom)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.5f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp)
                    ) {
                        var landscapeSlider by remember { mutableStateOf<Float?>(null) }
                        
                        // Timestamps row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(playbackState.currentPosition),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = formatDuration(playbackState.duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        
                        // Thin progress bar at very bottom
                        Slider(
                            value = landscapeSlider ?: playbackState.progress,
                            onValueChange = {
                                landscapeSlider = it
                                lastInteraction = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                landscapeSlider?.let { viewModel.seekToProgress(it) }
                                landscapeSlider = null
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.Red,
                                activeTrackColor = Color.Red,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        return
    }
    
    // Restore orientation to sensor when video mode is off in landscape
    LaunchedEffect(isVideoMode) {
        if (!isVideoMode && isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val isVideoActive = isVideoMode && videoStreamInfo != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isVideoActive) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                dynamicBgColor,
                                SpotifyBlack
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
                }
            )
            .pointerInput(Unit) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragAmount = 0f },
                    onDragEnd = {
                        if (totalDragAmount > 50f) {
                            onNavigateBack()
                        }
                        totalDragAmount = 0f
                    },
                    onDragCancel = { totalDragAmount = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) {
                            totalDragAmount += dragAmount
                        }
                    }
                )
            }
    ) {
        // ===== VIDEO BACKGROUND LAYER (behind everything) =====
        if (isVideoActive) {
            VideoPlayerView(
                videoUrl = videoStreamInfo!!.videoStreamUrl,
                currentPosition = playbackState.currentPosition,
                isPlaying = playbackState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark gradient scrim so controls are readable over video
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
        }
        
        // ===== CONTROLS LAYER (on top of video) =====
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Top bar with close and menu buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                IconButton(onClick = { viewModel.showOptionsSheet() }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Get previous and next track artwork
            val queue = playbackState.queue
            val currentIndex = playbackState.currentQueueIndex
            val previousTrack = if (currentIndex > 0) queue.getOrNull(currentIndex - 1) else null
            val nextTrack = if (currentIndex < queue.size - 1) queue.getOrNull(currentIndex + 1) else null
            
            // CAROUSEL-STYLE ALBUM ART SECTION
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = playbackState.currentQueueIndex,
                pageCount = { playbackState.queue.size.coerceAtLeast(1) }
            )
            
            // Sync pager with playback state
            LaunchedEffect(playbackState.currentQueueIndex) {
                if (pagerState.currentPage != playbackState.currentQueueIndex) {
                    pagerState.scrollToPage(playbackState.currentQueueIndex)
                }
            }
            
            // Sync playback with pager
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != playbackState.currentQueueIndex && !pagerState.isScrollInProgress) {
                    viewModel.seekToQueueIndex(pagerState.currentPage)
                }
            }
            
            // Handle swipe settle
            LaunchedEffect(pagerState.isScrollInProgress) {
                if (!pagerState.isScrollInProgress) {
                    if (pagerState.currentPage != playbackState.currentQueueIndex) {
                        viewModel.seekToQueueIndex(pagerState.currentPage)
                    }
                }
            }

            // ===== ALBUM ART / EMPTY SPACE FOR VIDEO =====
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { accumulatedDragY = 0f },
                            onDragEnd = {
                                if (accumulatedDragY > 150) {
                                    onNavigateBack()
                                }
                                accumulatedDragY = 0f
                            },
                            onDragCancel = { accumulatedDragY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDragY += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // In video mode: empty space (video fills behind via background layer)
                // In normal mode: album art carousel
                if (!isVideoActive) {
                    val queue2 = playbackState.queue
                    
                    if (queue2.isEmpty()) {
                         AsyncImage(
                            model = null,
                            contentDescription = "No music",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifySurfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        androidx.compose.foundation.pager.HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 40.dp),
                            pageSpacing = 12.dp,
                            beyondBoundsPageCount = 5
                        ) { page ->
                            val track = queue2.getOrNull(page)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .graphicsLayer {
                                        val pageOffset = (
                                            (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                        ).absoluteValue
                                        alpha = if (pageOffset < 1.0f) 1f else 0.5f
                                    },
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                AsyncImage(
                                    model = track?.artworkUri,
                                    contentDescription = track?.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp)) // Reduced for tighter layout
            
            // Track info section (below album art)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Song title and like button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track?.title ?: "No track playing",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Explicit badge
                            Surface(
                                modifier = Modifier.padding(end = 6.dp),
                                color = TextSecondary.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(2.dp)
                            ) {
                                Text(
                                    text = "E",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyBlack,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = track?.artist ?: "Unknown Artist",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Like/Add button (+ or checkmark) - KEEP THIS
                    IconButton(onClick = { viewModel.onLikeButtonClick() }) {
                        if (isLiked) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(SpotifyGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Liked",
                                    tint = SpotifyBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "Add to favorites",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp)) // Tighter spacing
                
                // Progress bar
                Column {
                    Slider(
                        value = progress,
                        onValueChange = { sliderPosition = it },
                        onValueChangeFinished = {
                            sliderPosition?.let { viewModel.seekToProgress(it) }
                            sliderPosition = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = TextPrimary,
                            inactiveTrackColor = SpotifySurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(playbackState.currentPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = formatDuration(playbackState.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp)) // Tighter
                
                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleEnabled) SpotifyGreen else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Previous
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    // Play/Pause (white circle) — shows spinner when buffering
                    Box(
                        modifier = Modifier.size(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(TextPrimary, CircleShape)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = SpotifyBlack,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = SpotifyBlack,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                    
                    // Next
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    // Repeat/Timer
                    IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                        Icon(
                            imageVector = when (playbackState.repeatMode) {
                                RepeatMode.ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (playbackState.repeatMode != RepeatMode.OFF) SpotifyGreen else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp)) // Tighter
                
                // Bottom row: device, share, queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Device",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpotifyGreen
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Download Button
                    val downloadStatus by viewModel.downloadStatus.collectAsState()
                    val context = LocalContext.current
                    
                    IconButton(onClick = {
                        when (downloadStatus) {
                            com.audioflow.player.data.local.entity.DownloadStatus.COMPLETED -> {
                                // Already downloaded - show toast, don't delete automatically
                                Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show()
                            }
                            com.audioflow.player.data.local.entity.DownloadStatus.DOWNLOADING -> {
                                // Cancel the download
                                viewModel.downloadCurrentTrack() // toggleDownload will cancel it
                                Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // Start download
                                viewModel.downloadCurrentTrack()
                                Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        when (downloadStatus) {
                            com.audioflow.player.data.local.entity.DownloadStatus.DOWNLOADING -> {
                                val infiniteTransition = rememberInfiniteTransition(label = "download")
                                val angle by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = LinearEasing)
                                    ),
                                    label = "rotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.Refresh, // Clockwise rotation effect
                                    contentDescription = "Downloading - tap to cancel",
                                    tint = SpotifyGreen,
                                    modifier = Modifier.rotate(angle)
                                )
                            }
                            com.audioflow.player.data.local.entity.DownloadStatus.COMPLETED -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    tint = Color.Red // Red checkmark for downloaded songs
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    
                    Row {
                        // Video toggle button (replaces unused QueueMusic icon)
                        // Only visible for YouTube tracks per spec Section 1
                        if (track?.source == TrackSource.YOUTUBE) {
                            IconButton(onClick = { viewModel.toggleVideoMode() }) {
                                if (isVideoLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = SpotifyGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isVideoMode) Icons.Default.MusicVideo
                                                      else Icons.Default.OndemandVideo,
                                        contentDescription = if (isVideoMode) "Switch to music" else "Play video",
                                        tint = if (isVideoMode) SpotifyGreen else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Lyrics preview section (conditional on lyricsEnabled preference)
            if (lyrics != null && lyrics?.hasDisplayableContent() == true && lyricsEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    LyricsPreviewCard(
                        lyrics = lyrics,
                        currentPosition = playbackState.currentPosition,
                        onShowLyricsClick = { showLyricsScreen = true }
                    )
                }
            }
            
            // Extra padding for bottom nav
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Bottom sheet for playlists
        AddToPlaylistSheet(
            isVisible = showPlaylistSheet,
            isLiked = isLiked,
            playlists = playlists.map { playlist ->
                PlaylistItem(
                    id = playlist.id,
                    name = playlist.name,
                    songCount = playlist.trackIds.size,
                    thumbnailUri = playlist.thumbnailUri
                )
            },
            onDismiss = { viewModel.dismissPlaylistSheet() },
            onLikedSongsClick = { viewModel.onLikeButtonClick() },
            onNewFolderClick = { viewModel.createNewFolder() },
            onPlaylistClick = { viewModel.addToPlaylist(it.id) },
            onNewPlaylistClick = { viewModel.showCreatePlaylistDialog() },
            onRemoveFromLikedSongs = { viewModel.removeFromLikedSongs() }
        )
        
        // Dialog for creating new playlist
        if (showNewPlaylistDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.hideCreatePlaylistDialog() },
                title = { Text("New Playlist") },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.createNewPlaylist(newPlaylistName)
                            newPlaylistName = ""
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideCreatePlaylistDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Dialog for creating new folder
        if (showNewFolderDialog) {
            var newFolderName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.hideNewFolderDialog() },
                title = { Text("New Folder") },
                text = {
                    TextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.confirmNewFolder(newFolderName)
                            newFolderName = ""
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideNewFolderDialog() }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        // Options sheet (three-dots menu)
        PlayerOptionsSheet(
            isVisible = showOptionsSheet,
            track = track,
            lyricsEnabled = lyricsEnabled,
            onDismiss = { viewModel.dismissOptionsSheet() },
            onLyricsToggle = { viewModel.toggleLyrics() },
            onAddToPlaylist = { 
                viewModel.dismissOptionsSheet()
                viewModel.onLikeButtonClick() // This will show the playlist sheet
            },
            onGoToArtist = { viewModel.goToArtist() },
            onGoToAlbum = { viewModel.goToAlbum() },
            onViewCredits = { viewModel.viewCredits() },
            onSleepTimer = { viewModel.openSleepTimer() },
            onEqualizer = { viewModel.openEqualizer() }
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Video player composable using ExoPlayer via AndroidView.
 * Muted (audio from existing music player). Syncs position with audio player.
 * Handles lifecycle properly with DisposableEffect.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun VideoPlayerView(
    videoUrl: String,
    currentPosition: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Must match the User-Agent used in YouTubeExtractor to avoid 403
    val userAgent = "com.google.android.youtube/19.09.36 (Linux; U; Android 14) gzip"

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f // Mute — audio from music player
            repeatMode = Player.REPEAT_MODE_ALL
            
            val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
            
            val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl))
                
            setMediaSource(mediaSource)
            prepare()
            seekTo(currentPosition)
            playWhenReady = isPlaying
        }
    }

    // Sync play/pause with music player
    LaunchedEffect(isPlaying) {
        if (isPlaying && !exoPlayer.isPlaying) exoPlayer.play()
        else if (!isPlaying && exoPlayer.isPlaying) exoPlayer.pause()
    }
    
    // Drift correction: if video drifts >2s from audio, snap back
    LaunchedEffect(currentPosition) {
        val drift = (exoPlayer.currentPosition - currentPosition).absoluteValue
        if (drift > 2000) exoPlayer.seekTo(currentPosition)
    }

    // Cleanup
    DisposableEffect(videoUrl) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                // Center-crop: fill entire screen, may crop edges but no letterboxing
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier
    )
}
