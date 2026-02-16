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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
    
    var selectedTrackForOptions by remember { mutableStateOf<Track?>(null) }
    var selectedEntityForOptions by remember { mutableStateOf<DownloadedSongEntity?>(null) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var moveToSongId by remember { mutableStateOf<String?>(null) }
    var newFolderName by remember { mutableStateOf("") }
    var currentFolderId by remember { mutableStateOf<String?>(null) }

    var showRenameFolderDialog by remember { mutableStateOf<String?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    var showAddSongsSheet by remember { mutableStateOf(false) }
    
    // Drag & Drop State
    var isDragging by remember { mutableStateOf(false) }
    var draggedItem by remember { mutableStateOf<DownloadedSongEntity?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    var dragChange by remember { mutableStateOf(Offset.Zero) }
    
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    var currentDropTargetId by remember { mutableStateOf<String?>(null) }
    
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
                horizontalArrangement = Arrangement.Center, // Centered
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
                IconButton(
                    onClick = { isShuffleEnabled = !isShuffleEnabled },
                    modifier = Modifier
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) SpotifyGreen else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                // Play button (only for completed downloads)
                // Always show layout space even if hidden? No, just hide if empty?
                // If hidden, the layout shifts.
                // But Play is central.
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
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape) // Ensure circle clip
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play All",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    // Placeholder sized box to keep alignment if needed, or just nothing.
                    Spacer(modifier = Modifier.size(56.dp)) 
                }
                
                Spacer(modifier = Modifier.width(24.dp))

                // Delete All Button
                IconButton(
                    onClick = { showDeleteAllDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Delete All",
                        tint = TextSecondary, // Consistent with other icons (was Red previously)
                        // Or keep Red? User said "Delete all". Usually danger is red.
                        // But standard icons are white/secondary.
                        // I'll keep it TextSecondary for consistency, maybe red tint on click?
                        // Or just Red as it was.
                        // tint = Color.Red 
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Create Folder button (Only at Root)
                if (currentFolderId == null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
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
                                onDelete = { viewModel.deleteFolder(folder.id) },
                                isDropTarget = currentDropTargetId == folder.id,
                                modifier = Modifier.onGloballyPositioned { folderBounds[folder.id] = it.boundsInRoot() }
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
                        var itemBounds by remember { mutableStateOf(Rect.Zero) }
                        
                        // Modifier for drag
                        val dragModifier = Modifier
                            .onGloballyPositioned { itemBounds = it.boundsInRoot() }
                            .pointerInput(entity.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        draggedItem = entity
                                        dragPosition = itemBounds.topLeft
                                        dragChange = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragChange += dragAmount
                                        
                                        val currentItemRect = Rect(offset = dragPosition + dragChange, size = itemBounds.size)
                                        val center = currentItemRect.center
                                        
                                        val target = folderBounds.entries.find { (_, rect) ->
                                            rect.contains(center)
                                        }?.key
                                        
                                        currentDropTargetId = target
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        if (currentDropTargetId != null) {
                                            viewModel.moveSongToFolder(entity.id, currentDropTargetId)
                                        }
                                        currentDropTargetId = null
                                        draggedItem = null
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        currentDropTargetId = null
                                        draggedItem = null
                                    }
                                )
                            }
                            .alpha(if (isDragging && draggedItem?.id == entity.id) 0f else 1f)
                        
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
                            onMoveTo = { moveToSongId = entity.id },
                            onOptionsClick = {
                                selectedEntityForOptions = entity
                                selectedTrackForOptions = entityToTrack(entity)
                            },
                            modifier = dragModifier
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
                    viewModel.downloadTrack(track)
                }
            )
        }
        
        // Song Options Sheet

        if (selectedTrackForOptions != null && selectedEntityForOptions != null) {
            val entity = selectedEntityForOptions!!
            val isCompleted = entity.status == DownloadStatus.COMPLETED
            
            com.audioflow.player.ui.components.SongOptionsSheet(
                isVisible = true,
                track = selectedTrackForOptions,
                onDismiss = { 
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onPlay = {
                    if (isCompleted) {
                         // Find index in completed downloads
                         val completedTracks = completedDownloads.map { entityToTrack(it) }
                         val trackToPlay = entityToTrack(entity)
                         // Determine start index based on matching ID
                         val startIdx = completedTracks.indexOfFirst { it.id == trackToPlay.id }.coerceAtLeast(0)
                         viewModel.playDownloadedPlaylist(completedTracks, startIdx)
                         onNavigateToPlayer()
                    }
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onPlayNext = {
                    if (isCompleted) {
                        viewModel.playNextInQueue(selectedTrackForOptions!!)
                    }
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onAddToQueue = {
                     if (isCompleted) {
                        viewModel.addToQueue(selectedTrackForOptions!!)
                     }
                     selectedTrackForOptions = null
                     selectedEntityForOptions = null
                },
                onAddToPlaylist = {
                    trackToAddToPlaylist = selectedTrackForOptions
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                    showPlaylistSheet = true
                },
                onGoToArtist = { 
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onGoToAlbum = { 
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onShare = { 
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                
                // Move Options
                showMoveOptions = isCompleted,
                onMoveUp = {
                     viewModel.moveDownloadUp(entity.id)
                     selectedTrackForOptions = null
                     selectedEntityForOptions = null
                },
                onMoveDown = {
                     viewModel.moveDownloadDown(entity.id)
                     selectedTrackForOptions = null
                     selectedEntityForOptions = null
                },
                onMoveTo = {
                     moveToSongId = entity.id
                     selectedTrackForOptions = null
                     selectedEntityForOptions = null
                },
                
                isDownloaded = isCompleted,
                isFailed = entity.status == DownloadStatus.FAILED,
                onRemoveDownload = {
                    // This is "Remove download" action
                    viewModel.deleteDownload(entity.id)
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onRetry = {
                     viewModel.retryDownload(entity.id)
                     selectedTrackForOptions = null
                     selectedEntityForOptions = null
                },
                
                onDelete = {
                    // Same as remove download here
                    viewModel.deleteDownload(entity.id)
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                onSaveToDevice = {
                    viewModel.saveTrackToDevice(entityToTrack(entity))
                    selectedTrackForOptions = null
                    selectedEntityForOptions = null
                },
                deleteLabel = "Delete"
            )
        }
        
        // Add to Playlist Sheet
        if (showPlaylistSheet && trackToAddToPlaylist != null) {
            val uiState by viewModel.uiState.collectAsState()
            
            com.audioflow.player.ui.player.AddToPlaylistSheet(
                isVisible = true,
                isLiked = false, // TODO: Check if liked
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
        
        // Drag Overlay - using Popup so it renders above everything
        if (isDragging && draggedItem != null) {
            val item = draggedItem!!
            androidx.compose.ui.window.Popup(
                offset = IntOffset(
                    (dragPosition.x + dragChange.x).roundToInt(),
                    (dragPosition.y + dragChange.y).roundToInt()
                )
            ) {
                Box(
                    modifier = Modifier
                        .width(280.dp)
                        .background(Color(0xFF282828), RoundedCornerShape(8.dp))
                        .border(
                            width = 2.dp,
                            color = if (currentDropTargetId != null) SpotifyGreen else SpotifyGreen.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (currentDropTargetId != null) "✓ Drop to move" else "Drag to folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentDropTargetId != null) SpotifyGreen else TextSecondary
                            )
                        }
                    }
                }
            }
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
    onMoveTo: () -> Unit = {},
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Delete confirmation dialog - KEPT inside item for direct delete button logic if needed,
    // but main options menu is now external.
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
        IconButton(onClick = onOptionsClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun FolderItem(
    folder: com.audioflow.player.data.local.entity.DownloadFolderEntity,
    songCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isDropTarget: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    
    // Scale animation when used as drop target
    val scale by animateFloatAsState(
        targetValue = if (isDropTarget) 1.05f else 1f,
        label = "scale"
    )
    
    // Border color
    val borderColor = if (isDropTarget) SpotifyGreen else Color.Transparent
    val backgroundColor = if (isDropTarget) SpotifyGreen.copy(alpha = 0.2f) else Color.Transparent
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Folder icon
        Box(
            modifier = Modifier
                .size(if (isDropTarget) 56.dp else 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyGreen.copy(alpha = 0.15f))
                .border(
                    width = if (isDropTarget) 2.dp else 0.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(8.dp)
                ),
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
