package com.audioflow.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.audioflow.player.ui.theme.*

/**
 * Playlist data for the bottom sheet
 */
data class PlaylistItem(
    val id: String,
    val name: String,
    val songCount: Int,
    val thumbnailUri: String? = null,
    val isLikedSongs: Boolean = false,
    val containsSong: Boolean = false
)

/**
 * Bottom sheet for adding a song to playlists
 * Matches Spotify's "Saved in" bottom sheet design
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    isVisible: Boolean,
    isLiked: Boolean,
    playlists: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onLikedSongsClick: () -> Unit,
    onNewFolderClick: () -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onNewPlaylistClick: () -> Unit,
    onRemoveFromLikedSongs: () -> Unit
) {
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = SpotifySurface,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(32.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextSecondary)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved in",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    TextButton(onClick = onNewPlaylistClick) {
                        Text(
                            text = "New playlist",
                            color = SpotifyGreen
                        )
                    }
                }
                
                // Liked Songs
                SheetListItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF5B4CEB),
                                            Color(0xFFB4AFF8)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = "Liked Songs",
                    subtitle = null,
                    isAdded = isLiked,
                    onClick = onLikedSongsClick
                )
                
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = SpotifySurfaceVariant
                )
                
                // New Folder
                SheetListItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifySurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = "New Folder",
                    subtitle = null,
                    isAdded = false,
                    onClick = onNewFolderClick
                )
                
                // Existing playlists
                playlists.forEach { playlist ->
                    SheetListItem(
                        icon = {
                            if (playlist.thumbnailUri != null) {
                                AsyncImage(
                                    model = playlist.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpotifySurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }
                        },
                        title = playlist.name,
                        subtitle = "${playlist.songCount} song${if (playlist.songCount != 1) "s" else ""}",
                        isAdded = playlist.containsSong,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
                
                // New Playlist
                SheetListItem(
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SpotifySurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    title = "New playlist",
                    subtitle = null,
                    isAdded = false,
                    onClick = onNewPlaylistClick
                )
                
                // Remove from Liked Songs (only show if liked)
                if (isLiked) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = onRemoveFromLikedSongs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Remove from Liked Songs",
                            color = ErrorRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    isAdded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        
        if (isAdded) {
            // Green checkmark when song IS in this playlist/liked
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Added",
                tint = SpotifyGreen,
                modifier = Modifier.size(24.dp)
            )
        } else {
            // Plus icon when song is NOT in this playlist
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
