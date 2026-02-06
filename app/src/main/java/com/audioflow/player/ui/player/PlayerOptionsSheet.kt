package com.audioflow.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.audioflow.player.model.Track
import com.audioflow.player.ui.theme.*

/**
 * Options bottom sheet for the Now Playing screen
 * Displays various actions for the current track
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    isVisible: Boolean,
    track: Track?,
    lyricsEnabled: Boolean,
    onDismiss: () -> Unit,
    onLyricsToggle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onViewCredits: () -> Unit,
    onSleepTimer: () -> Unit,
    onEqualizer: () -> Unit
) {
    if (!isVisible) return
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SpotifySurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Track info header
            if (track != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album artwork
                    AsyncImage(
                        model = track.artworkUri,
                        contentDescription = track.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                HorizontalDivider(
                    color = SpotifySurfaceVariant,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            
            // Lyrics toggle
            OptionsSheetItem(
                icon = Icons.Default.MusicNote,
                title = "Lyrics",
                subtitle = if (lyricsEnabled) "On" else "Off",
                showToggle = true,
                isEnabled = lyricsEnabled,
                onClick = onLyricsToggle
            )
            
            // Add to playlist
            OptionsSheetItem(
                icon = Icons.Default.PlaylistAdd,
                title = "Add to playlist",
                onClick = onAddToPlaylist
            )
            
            // Go to artist
            OptionsSheetItem(
                icon = Icons.Default.Person,
                title = "Go to artist",
                onClick = onGoToArtist
            )
            
            // Go to album
            OptionsSheetItem(
                icon = Icons.Default.Album,
                title = "Go to album",
                onClick = onGoToAlbum
            )
            
            HorizontalDivider(
                color = SpotifySurfaceVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            
            // View credits
            OptionsSheetItem(
                icon = Icons.Default.Info,
                title = "View credits",
                onClick = onViewCredits
            )
            
            // Sleep timer
            OptionsSheetItem(
                icon = Icons.Default.Timer,
                title = "Sleep timer",
                onClick = onSleepTimer
            )
            
            // Equalizer
            OptionsSheetItem(
                icon = Icons.Default.Equalizer,
                title = "Equalizer",
                onClick = onEqualizer
            )
        }
    }
}

@Composable
private fun OptionsSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    showToggle: Boolean = false,
    isEnabled: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            if (subtitle != null && !showToggle) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        if (showToggle) {
            // Toggle indicator
            Surface(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = if (isEnabled) SpotifyGreen else SpotifySurfaceVariant
            ) {
                Text(
                    text = if (isEnabled) "On" else "Off",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) SpotifyBlack else TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
