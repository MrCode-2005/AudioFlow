package com.audioflow.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.audioflow.player.model.Track
import com.audioflow.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    isVisible: Boolean,
    track: Track?,
    onDismiss: () -> Unit,
    // Actions
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onShare: () -> Unit,
    // Context-specific actions
    showMoveOptions: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onMoveTo: (() -> Unit)? = null, // "Move to" (folder/playlist)
    // Download actions
    isDownloaded: Boolean = false,
    isFailed: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    // Save to device (public storage)
    onSaveToDevice: (() -> Unit)? = null,
    // Delete action (from playlist or liked)
    onDelete: (() -> Unit)? = null,
    deleteLabel: String = "Remove"
) {
    if (isVisible && track != null) {
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
                // Header with track info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track.artworkUri != null) {
                        AsyncImage(
                            model = track.artworkUri,
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
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist ?: "Unknown Artist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Divider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = SpotifySurfaceVariant
                )
                
                // Play actions
                OptionItem(
                    icon = Icons.Default.PlayArrow,
                    text = "Play",
                    onClick = onPlay
                )
                
                OptionItem(
                    icon = Icons.Default.QueueMusic,
                    text = "Play next",
                    onClick = onPlayNext
                )

                OptionItem(
                    icon = Icons.Default.PlaylistAdd,
                    text = "Add to playing queue",
                    onClick = onAddToQueue
                )
                
                OptionItem(
                    icon = Icons.Default.AddCircleOutline,
                    text = "Add to playlist",
                    onClick = onAddToPlaylist
                )
                
                // Move options (for playlists)
                if (showMoveOptions) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = SpotifySurfaceVariant)
                    
                    if (onMoveUp != null) {
                        OptionItem(
                            icon = Icons.Default.ArrowUpward,
                            text = "Move up",
                            onClick = onMoveUp
                        )
                    }
                    
                    if (onMoveDown != null) {
                        OptionItem(
                            icon = Icons.Default.ArrowDownward,
                            text = "Move down",
                            onClick = onMoveDown
                        )
                    }
                    
                    if (onMoveTo != null) {
                         OptionItem(
                            icon = Icons.Outlined.DriveFileMove, // Requires outlined icon
                            text = "Move to...",
                            onClick = onMoveTo
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp), color = SpotifySurfaceVariant)
                
                // Download actions
                if (isFailed) {
                    if (onRetry != null) {
                        OptionItem(
                            icon = Icons.Default.Refresh,
                            text = "Retry download",
                            onClick = onRetry,
                            tint = Color.Red
                        )
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = SpotifySurfaceVariant)
                    }
                } else if (isDownloaded) {
                    if (onRemoveDownload != null) {
                        OptionItem(
                            icon = Icons.Outlined.DownloadForOffline,
                            text = "Remove download",
                            onClick = onRemoveDownload,
                            tint = SpotifyGreen // Highlight valid download
                        )
                    }
                } else {
                    if (onDownload != null) {
                        OptionItem(
                            icon = Icons.Default.Download,
                            text = "Download",
                            onClick = onDownload
                        )
                    }
                }
                
                // Save to device (public storage folder)
                if (onSaveToDevice != null) {
                    OptionItem(
                        icon = Icons.Default.PhoneAndroid,
                        text = "Save to device",
                        onClick = onSaveToDevice
                    )
                }
                
                // Navigation actions
                OptionItem(
                    icon = Icons.Default.Person,
                    text = "Go to artist",
                    onClick = onGoToArtist
                )
                
                OptionItem(
                    icon = Icons.Default.Album,
                    text = "Go to album",
                    onClick = onGoToAlbum
                )
                
                OptionItem(
                    icon = Icons.Default.Share,
                    text = "Share",
                    onClick = onShare
                )
                
                // Delete action
                if (onDelete != null) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = SpotifySurfaceVariant)
                    OptionItem(
                        icon = Icons.Outlined.Delete,
                        text = deleteLabel,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = tint
        )
    }
}
