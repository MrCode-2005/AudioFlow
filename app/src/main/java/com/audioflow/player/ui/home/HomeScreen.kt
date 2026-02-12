package com.audioflow.player.ui.home

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.data.local.RecentlyPlayedSong
import com.audioflow.player.data.remote.YouTubeSearchResult
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.*
import com.audioflow.player.ui.theme.*

@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val recentlyPlayedSongs by viewModel.recentlyPlayedSongs.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    
    // Show error snackbar when play error occurs
    LaunchedEffect(uiState.playError) {
        uiState.playError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearPlayError()
        }
    }
    
    // Load music and recommendations when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.loadMusic()
        viewModel.loadRecommendations()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (playbackState.currentTrack != null) 140.dp else 80.dp)
        ) {
            // Header with toggle
            item {
                HomeHeader(
                    isDynamicMode = uiState.isDynamicMode,
                    onToggleMode = { viewModel.toggleContentMode() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
            
            // ==================== LOCAL MODE: Your Library Only ====================
            if (!uiState.isDynamicMode) {
                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                    }
                } else if (uiState.allTracks.isEmpty()) {
                    item {
                        EmptyStateContent(
                            onRefresh = { viewModel.loadMusic() }
                        )
                    }
                } else {
                    // Your Library Header with Play button
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Library",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            
                            PlayButton(
                                isPlaying = playbackState.isPlaying,
                                onClick = { viewModel.playAllTracks() },
                                size = ButtonSize.MEDIUM
                            )
                        }
                    }
                    
                    // Local tracks list
                    itemsIndexed(uiState.allTracks) { index, track ->
                        TrackListItem(
                            track = track,
                            onClick = {
                                viewModel.playAllTracks(index)
                                onTrackClick(track)
                            }
                        )
                    }
                }
            }
            
            // ==================== DISCOVER MODE: Recommendations ====================
            if (uiState.isDynamicMode) {
                // Trending Now Section
                if (uiState.trendingSongs.isNotEmpty() || uiState.isTrendingLoading) {
                    item {
                        SectionHeader(title = "🔥 Trending Now")
                    }
                    
                    item {
                        if (uiState.isTrendingLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = SpotifyGreen)
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.trendingSongs) { song ->
                                    TrendingSongCard(
                                        song = song,
                                        onClick = {
                                            viewModel.playYouTubeResult(song, uiState.trendingSongs)
                                            onNavigateToPlayer()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
                
                // Made For You Section
                if (uiState.trendingPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader(title = "🎯 Made For You")
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.trendingPlaylists) { playlist ->
                                AutoPlaylistCard(
                                    playlist = playlist,
                                    onClick = {
                                        viewModel.playTrendingPlaylist(playlist)
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
                
                // Recently Played Section
                if (recentlyPlayedSongs.isNotEmpty()) {
                    item {
                        SectionHeader(title = "🕐 Recently Played")
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(recentlyPlayedSongs.take(10)) { song ->
                                RecentSongCard(
                                    song = song,
                                    onClick = {
                                        viewModel.playRecentSong(song)
                                        onNavigateToPlayer()
                                    }
                                )
                            }
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
                
                // Browse by Genre Section
                item {
                    SectionHeader(title = "🎵 Browse by Genre")
                }
                
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(uiState.genreCategories) { index, category ->
                            GenreChip(
                                category = category,
                                onClick = {
                                    viewModel.loadGenreCategory(index)
                                }
                            )
                        }
                    }
                }
                
                // Show loaded genre category songs
                uiState.genreCategories.forEachIndexed { index, category ->
                    if (category.songs.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = "${category.emoji} ${category.name}")
                        }
                        
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(category.songs) { song ->
                                    GenreSongCard(
                                        song = song,
                                        onClick = {
                                            viewModel.playGenreCategory(category, category.songs.indexOf(song))
                                            onNavigateToPlayer()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
        
        // Loading overlay while song is being prepared
        if (uiState.isPlayLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = SpotifyGreen)
                    Text(
                        text = "Loading song...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        // Error Snackbar
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (playbackState.currentTrack != null) 80.dp else 16.dp)
        )
        
        // Mini Player
        AnimatedVisibility(
            visible = playbackState.currentTrack != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            MiniPlayer(
                playbackState = playbackState,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.playNext() },
                onClick = onNavigateToPlayer,
                modifier = Modifier.padding(bottom = 0.dp)
            )
        }
    }
}

// ==================== HEADER WITH STYLED TOGGLE ====================

@Composable
private fun HomeHeader(
    isDynamicMode: Boolean = false,
    onToggleMode: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Good evening",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            
            Row {
                IconButton(onClick = { /* Notifications */ }) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary
                    )
                }
                
                IconButton(onClick = { /* History */ }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = TextPrimary
                    )
                }
                
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = TextPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // STYLED LOCAL / DISCOVER TOGGLE (Equal width, better styling)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SpotifySurfaceVariant)
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Local Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isDynamicMode) SpotifyGreen else Color.Transparent)
                        .clickable { if (isDynamicMode) onToggleMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Local",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (!isDynamicMode) SpotifyBlack else TextSecondary
                    )
                }
                
                // Discover Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDynamicMode) SpotifyGreen else Color.Transparent)
                        .clickable { if (!isDynamicMode) onToggleMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Discover",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDynamicMode) SpotifyBlack else TextSecondary
                    )
                }
            }
        }
    }
}

// ==================== RECOMMENDATION UI COMPONENTS ====================

@Composable
private fun TrendingSongCard(
    song: YouTubeSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Trending badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(SpotifyGreen, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🔥",
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AutoPlaylistCard(
    playlist: TrendingPlaylist,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Playlist cover image
            if (playlist.coverImageUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.coverImageUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback gradient placeholder
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SpotifyGreen.copy(alpha = 0.8f), SpotifyGreen.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "${playlist.songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun GenreChip(
    category: GenreCategory,
    onClick: () -> Unit
) {
    val isLoaded = category.songs.isNotEmpty()
    
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isLoaded) SpotifyGreen else SpotifySurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = category.emoji,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isLoaded) SpotifyBlack else TextPrimary,
                fontWeight = FontWeight.Medium
            )
            if (category.isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = if (isLoaded) SpotifyBlack else SpotifyGreen
                )
            }
        }
    }
}

@Composable
private fun GenreSongCard(
    song: YouTubeSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = song.title,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecentSongCard(
    song: RecentlyPlayedSong,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = song.thumbnailUri?.let { Uri.parse(it) },
            contentDescription = song.title,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyStateContent(
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No local music found",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add music to your device or switch to Discover",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = SpotifyGreen,
                contentColor = SpotifyBlack
            )
        ) {
            Text("Scan for music")
        }
    }
}
