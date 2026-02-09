package com.audioflow.player.ui.player

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
import androidx.compose.ui.draw.rotate
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.RepeatMode
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
    val likedSongIds by viewModel.likedSongIds.collectAsState()
    val showPlaylistSheet by viewModel.showPlaylistSheet.collectAsState()
    val showNewPlaylistDialog by viewModel.showNewPlaylistDialog.collectAsState()
    val showNewFolderDialog by viewModel.showNewFolderDialog.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val showOptionsSheet by viewModel.showOptionsSheet.collectAsState()
    val lyricsEnabled by viewModel.lyricsEnabled.collectAsState()
    val track = playbackState.currentTrack
    
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
            
            Spacer(modifier = Modifier.height(8.dp)) // Reduced to give more space to album art
            
            // Get previous and next track artwork
            val queue = playbackState.queue
            val currentIndex = playbackState.currentQueueIndex
            val previousTrack = if (currentIndex > 0) queue.getOrNull(currentIndex - 1) else null
            val nextTrack = if (currentIndex < queue.size - 1) queue.getOrNull(currentIndex + 1) else null
            
            // CAROUSEL-STYLE ALBUM ART SECTION
            // Uses HorizontalPager for smooth swiping and preloading
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = playbackState.currentQueueIndex,
                pageCount = { playbackState.queue.size.coerceAtLeast(1) }
            )
            
            // Sync pager with playback state (when track changes externally)
            LaunchedEffect(playbackState.currentQueueIndex) {
                if (pagerState.currentPage != playbackState.currentQueueIndex) {
                    pagerState.scrollToPage(playbackState.currentQueueIndex)
                }
            }
            
            // Sync playback with pager (when user swipes)
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage != playbackState.currentQueueIndex && !pagerState.isScrollInProgress) {
                    val targetIndex = pagerState.currentPage
                    if (targetIndex < playbackState.currentQueueIndex) {
                        viewModel.seekToQueueIndex(targetIndex)
                    } else if (targetIndex > playbackState.currentQueueIndex) {
                        viewModel.seekToQueueIndex(targetIndex)
                    }
                }
            }
            
            // Handle swipe actions to trigger playback change
            LaunchedEffect(pagerState.isScrollInProgress) {
                if (!pagerState.isScrollInProgress) {
                    // When scroll settles, ensure we're playing the centered song
                    if (pagerState.currentPage != playbackState.currentQueueIndex) {
                        viewModel.seekToQueueIndex(pagerState.currentPage)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f) // Fill available vertical space
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { accumulatedDragY = 0f },
                            onDragEnd = {
                                if (accumulatedDragY > 150) { // Threshold for dismiss
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
                val queue = playbackState.queue
                
                if (queue.isEmpty()) {
                     // Placeholder for empty queue
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
                        contentPadding = PaddingValues(horizontal = 40.dp), // Show thin strips on sides
                        pageSpacing = 12.dp, // Tighter spacing for thin strip effect
                        beyondBoundsPageCount = 5 // Preload 5 items each side for smooth swiping
                    ) { page ->
                        val track = queue.getOrNull(page)
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .graphicsLayer {
                                    // Calculate the absolute offset for the current page from the scroll position
                                    val pageOffset = (
                                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                    ).absoluteValue

                                    // Scale the page based on its distance from the center
                                    // Center item is 1f, side items are slightly smaller if desired
                                    // standard carousel often keeps them same size or slightly smaller
                                    // For "pixel perfect" to screenshot 1, they look full size but just cut off
                                    
                                    // We can keep it simple first: fully opaque
                                    alpha = if (pageOffset < 1.0f) 1f else 0.5f // Dim side items slightly? Reference image looks fully bright
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
                                    tint = SpotifyGreen // Green checkmark instead of red
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
