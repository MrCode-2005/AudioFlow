package com.audioflow.player.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audioflow.player.data.local.entity.LikedSongEntity
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.MiniPlayer
import com.audioflow.player.ui.components.TrackListItem
import com.audioflow.player.ui.theme.SpotifyBlack
import com.audioflow.player.ui.theme.SpotifyGreen
import com.audioflow.player.ui.theme.TextPrimary
import com.audioflow.player.ui.theme.TextSecondary

@Composable
fun LikedSongsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val likedSongs by viewModel.likedSongs.collectAsState(initial = emptyList())
    val playbackState by viewModel.playbackState.collectAsState()
    var showAddSongsSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<Track?>(null) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF450AF5), SpotifyBlack)
                        )
                    )
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Liked Songs",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${likedSongs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { 
                        viewModel.playLikedSongs(likedSongs, shuffle = true)
                        onNavigateToPlayer()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle Play",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Play (Big)
                FloatingActionButton(
                    onClick = { 
                        viewModel.playLikedSongs(likedSongs, shuffle = false)
                        onNavigateToPlayer()
                    },
                    containerColor = SpotifyGreen,
                    contentColor = Color.Black,
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(24.dp))
                
                // Download
                IconButton(
                    onClick = { viewModel.downloadLikedSongs(likedSongs) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download All",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Add Songs
                IconButton(
                    onClick = { showAddSongsSheet = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Songs",
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(likedSongs) { entity ->
                    val track = viewModel.likedEntityToTrack(entity)
                    
                    TrackListItem(
                        track = track,
                        onClick = { 
                            // Play with full liked songs queue for carousel support
                            val index = likedSongs.indexOf(entity)
                            viewModel.playLikedSongAtIndex(likedSongs, index)
                            onNavigateToPlayer()
                        },
                        onDeleteClick = { /* Remove from liked? */ },
                        onOptionsClick = { selectedTrackForOptions = track }
                    )
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
                    viewModel.toggleLike(track)
                }
            )
        }
        
        // Song Options Sheet
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
                    selectedTrackForOptions?.let { viewModel.playNextInQueue(it) }
                    selectedTrackForOptions = null
                },
                onAddToQueue = {
                     selectedTrackForOptions?.let { viewModel.addToQueue(it) }
                     selectedTrackForOptions = null
                },
                onAddToPlaylist = {
                    trackToAddToPlaylist = selectedTrackForOptions
                    selectedTrackForOptions = null
                    showPlaylistSheet = true
                },
                onGoToArtist = { selectedTrackForOptions = null },
                onGoToAlbum = { selectedTrackForOptions = null },
                onShare = { selectedTrackForOptions = null },
                isDownloaded = false, // TODO: Check download status
                onDownload = {
                    selectedTrackForOptions?.let { viewModel.downloadTrack(it) }
                    selectedTrackForOptions = null
                },
                onDelete = {
                    // Remove from Liked Songs?
                    // viewModel.toggleLike(selectedTrackForOptions)
                    selectedTrackForOptions = null
                },
                onSaveToDevice = {
                    selectedTrackForOptions?.let { viewModel.saveTrackToDevice(it) }
                    selectedTrackForOptions = null
                },
                deleteLabel = "Remove from Liked Songs"
            )
        }
        
        // Add to Playlist Sheet
        if (showPlaylistSheet && trackToAddToPlaylist != null) {
            val uiState by viewModel.uiState.collectAsState()
            
            com.audioflow.player.ui.player.AddToPlaylistSheet(
                isVisible = true,
                isLiked = true, // In Liked Songs screen
                playlists = uiState.playlists.map { playlist ->
                    com.audioflow.player.ui.player.PlaylistItem(
                        id = playlist.id,
                        name = playlist.name,
                        songCount = playlist.tracks.size,
                        thumbnailUri = playlist.artworkUri?.toString(),
                        containsSong = trackToAddToPlaylist?.id?.let { trackId -> 
                            playlist.tracks.any { it.id == trackId } 
                        } ?: false
                    )
                },
                onDismiss = { 
                    showPlaylistSheet = false 
                    trackToAddToPlaylist = null
                },
                onPlaylistClick = { playlistItem ->
                    trackToAddToPlaylist?.let { track ->
                         // Find layout playlist object or just use ID
                         val playlist = uiState.playlists.find { it.id == playlistItem.id }
                         if (playlist != null) {
                             viewModel.addTrackToPlaylist(track, playlist)
                         }
                    }
                    showPlaylistSheet = false
                    trackToAddToPlaylist = null
                },
                onLikedSongsClick = {},
                onNewFolderClick = {},
                onNewPlaylistClick = {},
                onRemoveFromLikedSongs = {}
            )
        }
    }
}
