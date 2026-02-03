package com.audioflow.player.ui.home

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.*
import com.audioflow.player.ui.theme.*

@Composable
fun HomeScreen(
    onTrackClick: (Track) -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    
    // Load music when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.loadMusic()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = SpotifyGreen
            )
        } else if (uiState.allTracks.isEmpty()) {
            EmptyStateContent(
                onRefresh = { viewModel.loadMusic() }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (playbackState.currentTrack != null) 140.dp else 80.dp)
            ) {
                // Header
                item {
                    HomeHeader()
                }
                
                // Recent Albums
                if (uiState.recentAlbums.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Recently Played")
                    }
                    
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.recentAlbums) { album ->
                                AlbumCard(
                                    title = album.name,
                                    subtitle = album.artist,
                                    artworkUri = album.artworkUri,
                                    onClick = { /* Navigate to album */ }
                                )
                            }
                        }
                    }
                }
                
                // All Songs Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Songs",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        
                        PlayButton(
                            isPlaying = playbackState.isPlaying,
                            onClick = { viewModel.playAllTracks() },
                            size = ButtonSize.MEDIUM
                        )
                    }
                }
                
                // Track list
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
                modifier = Modifier.padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
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
            
            IconButton(onClick = { /* Settings */ }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun EmptyStateContent(
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            text = "No music found",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Add some music to your device to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
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
