package com.audioflow.player.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.MiniPlayer
import com.audioflow.player.ui.components.TrackListItem
import com.audioflow.player.ui.components.AddSongsToSectionSheet
import com.audioflow.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    
    // Download state for playlist (simplified for now)
    var isDownloaded by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<Track?>(null) }
    
    // Dialog states
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editPlaylistName by remember { mutableStateOf("") }
    
    // Gradient based on artwork (placeholder logic, using green/grey)
    val gradientColors = listOf(
        SpotifyGreen.copy(alpha = 0.3f), // Top
        SpotifyBlack // Bottom
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // App Bar
            SmallTopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = Color.Transparent,
                    navigationIconContentColor = Color.White
                )
            )

            // Header Content
            playlist?.let { pl ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(gradientColors))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Artwork (Placeholder or grid)
                    Surface(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = SpotifySurfaceVariant,
                        shadowElevation = 8.dp
                    ) {
                         if (tracks.isNotEmpty()) {
                             AsyncImage(
                                 model = tracks.first().artworkUri,
                                 contentDescription = null,
                                 contentScale = ContentScale.Crop,
                                 modifier = Modifier.fillMaxSize()
                             )
                         } else {
                             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                 Icon(
                                     imageVector = androidx.compose.material.icons.Icons.Default.List,
                                     contentDescription = null,
                                     tint = TextSecondary,
                                     modifier = Modifier.size(64.dp)
                                 )
                             }
                         }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = pl.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (tracks.isNotEmpty()) {
                        Text(
                            text = "${tracks.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Action Buttons Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Shuffle Button
                        IconButton(onClick = { viewModel.shufflePlay() }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) SpotifyGreen else TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    
                        // Play Button
                        Button(
                            onClick = { viewModel.playAll() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                contentColor = SpotifyBlack
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying && tracks.any { it.id == playbackState.currentTrack?.id }) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        IconButton(onClick = { 
                            viewModel.downloadAll()
                            // Show toast/snackbar
                        }) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Download Playlist",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Add Songs Button
                        IconButton(onClick = { showAddSongsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Songs",
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // More / Edit Button
                        Box {
                            var showMenu by remember { mutableStateOf(false) }
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add Songs") },
                                    onClick = { 
                                        showMenu = false 
                                        showAddSongsSheet = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit Playlist") },
                                    onClick = { 
                                        showMenu = false
                                        editPlaylistName = playlist?.name ?: ""
                                        showEditDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = Color.Red) },
                                    onClick = { 
                                        showMenu = false
                                        showDeleteConfirmDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            }
            
            // Track List
            if (tracks.isEmpty() && playlist != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No songs in this playlist yet.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(tracks) { track ->
                        PlaylistTrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            onClick = { 
                                viewModel.playTrack(track)
                                onNavigateToPlayer()
                            },
                            onDeleteClick = { viewModel.deleteTrack(track) },
                            onMoveUpClick = { viewModel.moveTrackUp(track) },
                            onMoveDownClick = { viewModel.moveTrackDown(track) },
                            onDownloadClick = { viewModel.downloadTrack(track) },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }
        
            // Mini Player
        AnimatedVisibility(
            visible = playbackState.currentTrack != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            MiniPlayer(
                playbackState = playbackState,
                onPreviousClick = { viewModel.playPrevious() },
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.playNext() },
                onClick = onNavigateToPlayer,
                modifier = Modifier.padding(bottom = 0.dp)
            )
        }

        // Add Songs Sheet
        if (showAddSongsSheet) {
             com.audioflow.player.ui.components.AddSongsToSectionSheet(
                onDismiss = { showAddSongsSheet = false },
                onAddTrack = { track ->
                    viewModel.addTrack(track)
                }
            )
        }

        // Song Options Sheet
        
        if (selectedTrackForOptions != null) {
            com.audioflow.player.ui.components.SongOptionsSheet(
                isVisible = true,
                track = selectedTrackForOptions,
                onDismiss = { selectedTrackForOptions = null },
                onPlay = {
                    selectedTrackForOptions?.let { viewModel.playTrack(it) }
                    selectedTrackForOptions = null
                    onNavigateToPlayer()
                },
                onPlayNext = {
                    // TODO: Implement play next
                    selectedTrackForOptions = null
                },
                onAddToQueue = {
                     // TODO: Implement add to queue
                     selectedTrackForOptions = null
                },
                onAddToPlaylist = {
                    // TODO: Show add to playlist sheet
                    selectedTrackForOptions = null
                },
                onGoToArtist = { selectedTrackForOptions = null },
                onGoToAlbum = { selectedTrackForOptions = null },
                onShare = { selectedTrackForOptions = null },
                showMoveOptions = true,
                onMoveUp = {
                    selectedTrackForOptions?.let { viewModel.moveTrackUp(it) }
                    selectedTrackForOptions = null
                },
                onMoveDown = {
                    selectedTrackForOptions?.let { viewModel.moveTrackDown(it) }
                    selectedTrackForOptions = null
                },
                onMoveTo = {
                     // TODO: Implement Move To
                     selectedTrackForOptions = null
                },
                isDownloaded = false, // Check if downloaded
                onDownload = {
                    selectedTrackForOptions?.let { viewModel.downloadTrack(it) }
                    selectedTrackForOptions = null
                },
                onDelete = {
                    selectedTrackForOptions?.let { viewModel.deleteTrack(it) }
                    selectedTrackForOptions = null
                },
                onSaveToDevice = {
                    selectedTrackForOptions?.let { viewModel.saveTrackToDevice(it) }
                    selectedTrackForOptions = null
                },
                deleteLabel = "Remove from playlist"
            )
        }
    }
    
    // ... Dialogs ...
}

@Composable
private fun PlaylistTrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        AsyncImage(
            model = track.artworkUri,
            contentDescription = "Album art",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) SpotifyGreen else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Playing indicator
        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Now playing",
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = TextSecondary
            )
        }
    }
}
