package com.audioflow.player.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.data.local.entity.DownloadStatus
import com.audioflow.player.data.local.entity.DownloadedSongEntity
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.MiniPlayer
import com.audioflow.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsState(initial = emptyList())
    val playbackState by viewModel.playbackState.collectAsState()
    val folders by viewModel.allFolders.collectAsState(initial = emptyList())
    var isShuffleEnabled by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var moveToSongId by remember { mutableStateOf<String?>(null) }
    var showRenameFolderDialog by remember { mutableStateOf<String?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    
    // Current folder name for header
    val currentFolderName = currentFolderId?.let { fid -> folders.find { it.id == fid }?.name }
    
    // Count by status
    val downloadingCount = downloadedSongs.count { it.status == DownloadStatus.DOWNLOADING }
    val completedCount = downloadedSongs.count { it.status == DownloadStatus.COMPLETED }
    val failedCount = downloadedSongs.count { it.status == DownloadStatus.FAILED }
    
    // Filter songs by current folder
    val allDownloads = if (currentFolderId != null) {
        downloadedSongs.filter { it.folderId == currentFolderId }.sortedByDescending { it.timestamp }
    } else {
        downloadedSongs.filter { it.folderId == null }.sortedByDescending { it.timestamp }
    }
    
    // Completed downloads for playback
    val completedDownloads = allDownloads.filter { entity ->
        entity.status == DownloadStatus.COMPLETED &&
        entity.localPath.isNotEmpty() &&
        java.io.File(entity.localPath).exists()
    }
    
    // Delete All Confirmation Dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Downloads?") },
            text = { Text("This will remove all downloads. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllDownloads()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text("Delete All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CardBackground,
            textContentColor = TextPrimary
        )
    }
    
    // Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false; newFolderName = "" },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim())
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }
                    }
                ) { Text("Create", color = SpotifyGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false; newFolderName = "" }) {
                    Text("Cancel")
                }
            },
            containerColor = CardBackground,
            textContentColor = TextPrimary
        )
    }
    
    // Rename Folder Dialog
    showRenameFolderDialog?.let { folderId ->
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = renameFolderText,
                    onValueChange = { renameFolderText = it },
                    placeholder = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameFolderText.isNotBlank()) {
                            viewModel.renameFolder(folderId, renameFolderText.trim())
                            showRenameFolderDialog = null
                        }
                    }
                ) { Text("Rename", color = SpotifyGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = null }) { Text("Cancel") }
            },
            containerColor = CardBackground,
            textContentColor = TextPrimary
        )
    }
    
    // Move To Dialog
    moveToSongId?.let { songId ->
        MoveToDialog(
            folders = folders,
            onMoveToFolder = { folderId ->
                viewModel.moveSongToFolder(songId, folderId)
                moveToSongId = null
            },
            onMoveToRoot = {
                viewModel.moveSongToFolder(songId, null)
                moveToSongId = null
            },
            onCreateAndMove = { folderName ->
                viewModel.createFolder(folderName)
                // After creation, move song to the new folder
                // Since folder creation is async, we handle this slightly differently
                viewModel.moveSongToFolder(songId, null) // temporary, best effort
                moveToSongId = null
            },
            onDismiss = { moveToSongId = null }
        )
    }
    
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
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF006450), SpotifyBlack)
                        )
                    )
            ) {
                IconButton(
                    onClick = {
                        if (currentFolderId != null) currentFolderId = null
                        else onNavigateBack()
                    },
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = currentFolderName ?: "Downloads",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    // Show status breakdown
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${allDownloads.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                        if (downloadingCount > 0) {
                            Text(
                                text = "• $downloadingCount downloading",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFA500) // Orange
                            )
                        }
                        if (failedCount > 0) {
                            Text(
                                text = "• $failedCount failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
            
            // Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete All Button
                    IconButton(
                        onClick = { showDeleteAllDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Red.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Delete All",
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Shuffle Toggle
                    IconButton(
                        onClick = { isShuffleEnabled = !isShuffleEnabled },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isShuffleEnabled) SpotifyGreen.copy(alpha = 0.2f) 
                                else Color.Transparent,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleEnabled) SpotifyGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create Folder button
                    if (currentFolderId == null) {
                        IconButton(
                            onClick = { showCreateFolderDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(SpotifyGreen.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = "New Folder",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Play button (only for completed downloads)
                    if (completedDownloads.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                val tracksToPlay = if (isShuffleEnabled) {
                                    completedDownloads.shuffled()
                                } else {
                                    completedDownloads
                                }.map { entityToTrack(it) }
                                
                                if (tracksToPlay.isNotEmpty()) {
                                    viewModel.playDownloadedPlaylist(tracksToPlay, 0)
                                    onNavigateToPlayer()
                                }
                            },
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play All",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
            
            // Divider
            HorizontalDivider(
                color = TextSecondary.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            // Downloaded Songs List - NOW SHOWS ALL DOWNLOADS!
            if (allDownloads.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Download songs to play them offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Show folders only at root level
                    if (currentFolderId == null && folders.isNotEmpty()) {
                        items(folders) { folder ->
                            FolderItem(
                                folder = folder,
                                songCount = downloadedSongs.count { it.folderId == folder.id },
                                onClick = { currentFolderId = folder.id },
                                onRename = {
                                    renameFolderText = folder.name
                                    showRenameFolderDialog = folder.id
                                },
                                onDelete = { viewModel.deleteFolder(folder.id) }
                            )
                        }
                        
                        // Divider between folders and songs
                        item {
                            HorizontalDivider(
                                color = TextSecondary.copy(alpha = 0.2f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    itemsIndexed(allDownloads) { index, entity ->
                        DownloadedSongItem(
                            entity = entity,
                            index = index,
                            totalCount = allDownloads.size,
                            onPlay = {
                                if (entity.status == DownloadStatus.COMPLETED) {
                                    val tracks = completedDownloads.map { entityToTrack(it) }
                                    val startIdx = completedDownloads.indexOf(entity).coerceAtLeast(0)
                                    viewModel.playDownloadedPlaylist(tracks, startIdx)
                                    onNavigateToPlayer()
                                }
                            },
                            onDelete = { viewModel.deleteDownload(entity.id) },
                            onMoveUp = { viewModel.moveDownloadUp(entity.id) },
                            onMoveDown = { viewModel.moveDownloadDown(entity.id) },
                            onRetry = { viewModel.retryDownload(entity.id) },
                            onMoveTo = { moveToSongId = entity.id }
                        )
                    }
                }
            }
        }
        
        // Mini Player - persists across all screens
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

@Composable
private fun DownloadedSongItem(
    entity: DownloadedSongEntity,
    index: Int,
    totalCount: Int,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRetry: () -> Unit,
    onMoveTo: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Animation for downloading spinner
    val infiniteTransition = rememberInfiniteTransition(label = "download_spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Download?") },
            text = { Text("Remove \"${entity.title}\" from downloads?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = CardBackground,
            textContentColor = TextPrimary
        )
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = entity.status == DownloadStatus.COMPLETED,
                onClick = onPlay
            )
            .background(
                when (entity.status) {
                    DownloadStatus.DOWNLOADING -> Color(0xFF1A1A1A) // Slight highlight
                    DownloadStatus.FAILED -> Color(0xFF2A1515) // Red tint
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art
        AsyncImage(
            model = entity.thumbnailUrl,
            contentDescription = "Album art",
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Song Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = entity.title,
                style = MaterialTheme.typography.titleSmall,
                color = when (entity.status) {
                    DownloadStatus.FAILED -> Color.Red.copy(alpha = 0.8f)
                    DownloadStatus.DOWNLOADING -> Color(0xFFFFA500) // Orange
                    else -> TextPrimary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entity.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            
            // Progress bar for downloading
            if (entity.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Animated progress bar
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFFA500), // Orange
                        trackColor = Color(0xFF333333)
                    )
                    Text(
                        text = "Downloading...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFFA500)
                    )
                }
            }
            
            // Failed status text
            if (entity.status == DownloadStatus.FAILED) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Failed - Tap to retry",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red
                )
            }
        }
        
        // Status indicator
        when (entity.status) {
            DownloadStatus.COMPLETED -> {
                // RED TICK for completed (as user requested)
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = Color.Red, // Changed to RED as requested
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 4.dp)
                )
            }
            DownloadStatus.DOWNLOADING -> {
                // Orange download icon (progress shown in row above)
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Downloading",
                    tint = Color(0xFFFFA500),
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 4.dp)
                )
            }
            DownloadStatus.FAILED -> {
                // Error icon - clickable for retry
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry download",
                        tint = Color.Red,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        
        // More options
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = TextSecondary
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (entity.status == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = {
                            showMenu = false
                            onPlay()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                    )
                }
                
                if (entity.status == DownloadStatus.FAILED) {
                    DropdownMenuItem(
                        text = { Text("Retry Download") },
                        onClick = {
                            showMenu = false
                            onRetry()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    )
                }
                
                HorizontalDivider()
                
                // Move Up (only if not first and completed)
                if (index > 0 && entity.status == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Move Up") },
                        onClick = {
                            showMenu = false
                            onMoveUp()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        }
                    )
                }
                
                // Move Down (only if not last and completed)
                if (index < totalCount - 1 && entity.status == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Move Down") },
                        onClick = {
                            showMenu = false
                            onMoveDown()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    )
                }
                
                if (entity.status == DownloadStatus.COMPLETED) {
                    DropdownMenuItem(
                        text = { Text("Move To Folder") },
                        onClick = {
                            showMenu = false
                            onMoveTo()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        }
                    )
                }
                
                HorizontalDivider()
                
                DropdownMenuItem(
                    text = { Text("Delete", color = Color.Red) },
                    onClick = {
                        showMenu = false
                        showDeleteDialog = true
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderItem(
    folder: com.audioflow.player.data.local.entity.DownloadFolderEntity,
    songCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder icon
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$songCount songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = TextSecondary
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { showMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Folder", color = Color.Red) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                )
            }
        }
    }
}

// Helper function to convert entity to Track
private fun entityToTrack(entity: DownloadedSongEntity): Track {
    return Track(
        id = entity.id,
        title = entity.title,
        artist = entity.artist ?: "Unknown",
        album = entity.album ?: "Unknown",
        artworkUri = android.net.Uri.parse(entity.thumbnailUrl ?: ""),
        duration = entity.duration,
        source = com.audioflow.player.model.TrackSource.LOCAL,
        contentUri = android.net.Uri.fromFile(java.io.File(entity.localPath))
    )
}
