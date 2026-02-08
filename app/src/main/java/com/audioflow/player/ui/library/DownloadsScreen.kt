package com.audioflow.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.TrackListItem
import com.audioflow.player.ui.theme.SpotifyBlack
import com.audioflow.player.ui.theme.SpotifyGreen
import com.audioflow.player.ui.theme.TextPrimary

@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsState(initial = emptyList())
    
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
                            colors = listOf(Color(0xFF006450), SpotifyBlack)
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
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${downloadedSongs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary.copy(alpha = 0.7f)
                    )
                }
            }
            
            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(downloadedSongs) { entity ->
                    val track = Track(
                        id = entity.id,
                        title = entity.title,
                        artist = entity.artist ?: "Unknown",
                        album = entity.album ?: "Unknown",
                        artworkUri = android.net.Uri.parse(entity.thumbnailUrl ?: ""),
                        duration = entity.duration,
                        source = com.audioflow.player.model.TrackSource.LOCAL,
                        contentUri = android.net.Uri.parse(entity.localPath)
                    )
                    
                    TrackListItem(
                        track = track,
                        onClick = { 
                            viewModel.playTrack(track)
                            onNavigateToPlayer()
                        },
                        onDeleteClick = { /* Delete download? */ }
                    )
                }
            }
        }
    }
}
