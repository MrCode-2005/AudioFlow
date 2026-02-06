package com.audioflow.player.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.audioflow.player.model.RepeatMode
import com.audioflow.player.ui.theme.*

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@OptIn(UnstableApi::class)
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLyrics: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val showPlaylistSheet by viewModel.showPlaylistSheet.collectAsState()
    val showNewPlaylistDialog by viewModel.showNewPlaylistDialog.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val videoStreamUrl by viewModel.videoStreamUrl.collectAsState()
    val showOptionsSheet by viewModel.showOptionsSheet.collectAsState()
    val lyricsEnabled by viewModel.lyricsEnabled.collectAsState()
    val track = playbackState.currentTrack
    
    // Check if current track is liked
    val isLiked = track?.id?.let { likedSongIds.contains(it) } ?: false
    
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    var showLyricsScreen by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    
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
    
    // Video player state
    val context = LocalContext.current
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            volume = 0f // Mute video (audio comes from main player)
        }
    }
    
    // Update video when URL changes
    LaunchedEffect(videoStreamUrl) {
        if (!videoStreamUrl.isNullOrBlank()) {
            videoPlayer.setMediaItem(MediaItem.fromUri(videoStreamUrl!!))
            videoPlayer.prepare()
            videoPlayer.play()
        } else {
            videoPlayer.stop()
        }
    }
    
    // Reset swipe processing when track changes (enables swiping again)
    LaunchedEffect(track?.id) {
        isSwipeProcessing = false
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
        }
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

    // Track video player error state
    var videoError by remember { mutableStateOf(false) }
    
    // Listen for video player errors
    LaunchedEffect(videoPlayer) {
        videoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                videoError = true
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .pointerInput(Unit) {
                var totalDragAmount = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragAmount = 0f },
                    onDragEnd = {
                        // Trigger navigation if dragged down enough (50dp threshold)
                        if (totalDragAmount > 50f) {
                            onNavigateBack()
                        }
                        totalDragAmount = 0f
                    },
                    onDragCancel = { totalDragAmount = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0) { // Only track downward drag
                            totalDragAmount += dragAmount
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Main player content with gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.5f) // Tall aspect for video-like area
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                SpotifyGreen.copy(alpha = 0.4f),
                                SpotifyBlack.copy(alpha = 0.9f),
                                SpotifyBlack
                            )
                        )
                    )
            ) {
                // Video background or Album art (fallback if video error or no URL)
                if (!videoStreamUrl.isNullOrBlank() && !videoError) {
                    // Video player background
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = videoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isSwipeProcessing) {
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulatedDragX = 0f },
                                    onDragEnd = {
                                        if (!isSwipeProcessing) {
                                            when {
                                                accumulatedDragX < -150 -> {
                                                    isSwipeProcessing = true
                                                    viewModel.next()
                                                }
                                                accumulatedDragX > 150 -> {
                                                    isSwipeProcessing = true
                                                    viewModel.previous()
                                                }
                                            }
                                        }
                                        accumulatedDragX = 0f
                                    },
                                    onDragCancel = { accumulatedDragX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragX += dragAmount
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { accumulatedDragY = 0f },
                                    onDragEnd = {
                                        if (accumulatedDragY > 200) {
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
                            }
                    )
                } else {
                    // Album art fallback (always show thumbnail)
                    AsyncImage(
                        model = track?.artworkUri,
                        contentDescription = "Album art",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isSwipeProcessing) {
                                detectHorizontalDragGestures(
                                    onDragStart = { accumulatedDragX = 0f },
                                    onDragEnd = {
                                        if (!isSwipeProcessing) {
                                            when {
                                                accumulatedDragX < -150 -> {
                                                    isSwipeProcessing = true
                                                    viewModel.next()
                                                }
                                                accumulatedDragX > 150 -> {
                                                    isSwipeProcessing = true
                                                    viewModel.previous()
                                                }
                                            }
                                        }
                                        accumulatedDragX = 0f
                                    },
                                    onDragCancel = { accumulatedDragX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragX += dragAmount
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { accumulatedDragY = 0f },
                                    onDragEnd = {
                                        if (accumulatedDragY > 200) {
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
                        contentScale = ContentScale.Crop
                    )
                }

                
                // Dark overlay for readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    SpotifyBlack.copy(alpha = 0.7f),
                                    SpotifyBlack
                                ),
                                startY = 300f
                            )
                        )
                )
                
                // Top bar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                if (dragAmount > 50) {
                                    onNavigateBack()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "PLAYING FROM ALBUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = track?.album ?: "Unknown Album",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    IconButton(onClick = { viewModel.showOptionsSheet() }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = TextPrimary
                        )
                    }
                }
                
                // Bottom content overlay (track info, controls)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                ) {
                    // Track info row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album art thumbnail + info
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track?.artworkUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track?.title ?: "No track playing",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Explicit badge (optional)
                                    Surface(
                                        modifier = Modifier.padding(end = 4.dp),
                                        color = TextSecondary,
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
                                        text = track?.artist ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        // Like button (+ or green checkmark)
                        IconButton(onClick = { viewModel.onLikeButtonClick() }) {
                            if (isLiked) {
                                // Green checkmark circle
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
                                // Plus icon
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = "Add to favorites",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                        
                        // Play/Pause (white circle)
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(TextPrimary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = SpotifyBlack,
                                modifier = Modifier.size(36.dp)
                            )
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
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
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
                        
                        Row {
                            IconButton(onClick = { /* Share */ }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { /* Queue */ }) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Queue",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Lyrics preview section (conditional on lyricsEnabled preference)
            if (lyrics != null && lyricsEnabled) {
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
            Spacer(modifier = Modifier.height(100.dp))
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
