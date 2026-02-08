package com.audioflow.player.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.Album
import com.audioflow.player.model.Artist
import com.audioflow.player.model.Playlist
import com.audioflow.player.model.Track
import com.audioflow.player.ui.components.*
import com.audioflow.player.ui.theme.*

@Composable
fun LibraryScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToLikedSongs: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState(initial = emptyList())
    val downloadedSongs by viewModel.downloadedSongs.collectAsState(initial = emptyList())
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            LibraryHeader(
                showAddButton = uiState.selectedFilter == LibraryFilter.PLAYLISTS,
                onAddClick = { showCreatePlaylistDialog = true }
            )
            
            // Filter chips
            FilterChipsRow(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.selectFilter(it) }
            )
            
            // Content based on selected filter
            when (uiState.selectedFilter) {
                LibraryFilter.SONGS -> {
                    SongsContent(
                        tracks = uiState.tracks,
                        onTrackClick = { viewModel.playTrack(it) },
                        onDeleteClick = { viewModel.deleteTrack(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                LibraryFilter.ALBUMS -> {
                    AlbumsGrid(
                        albums = uiState.albums,
                        onAlbumClick = { /* Navigate to album */ },
                        modifier = Modifier.weight(1f)
                    )
                }
                LibraryFilter.ARTISTS -> {
                    ArtistsGrid(
                        artists = uiState.artists,
                        onArtistClick = { /* Navigate to artist */ },
                        modifier = Modifier.weight(1f)
                    )
                }
                LibraryFilter.PLAYLISTS -> {
                    PlaylistsContent(
                        playlists = uiState.playlists,
                        likedSongsCount = likedSongs.size,
                        downloadedSongsCount = downloadedSongs.size,
                        onPlaylistClick = { onNavigateToPlaylist(it.id) },
                        onLikedSongsClick = onNavigateToLikedSongs,
                        onDownloadsClick = onNavigateToDownloads,
                        onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                        onDeletePlaylistClick = { viewModel.deletePlaylist(it) },
                        onRenamePlaylistClick = { playlist, newName -> viewModel.renamePlaylist(playlist, newName) },
                        onMovePlaylistUp = { viewModel.movePlaylistUp(it) },
                        onMovePlaylistDown = { viewModel.movePlaylistDown(it) },
                        modifier = Modifier.weight(1f)
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
                modifier = Modifier.padding(bottom = 0.dp)
            )
        }
        
        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                }
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    showAddButton: Boolean = true,
    onAddClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User avatar
            Surface(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                color = SpotifySurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.padding(6.dp),
                    tint = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }
        
        Row {
            IconButton(onClick = { /* Search */ }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextPrimary
                )
            }
            if (showAddButton) {
                IconButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedFilter: LibraryFilter,
    onFilterSelected: (LibraryFilter) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        item {
            FilterChip(
                text = "Playlists",
                selected = selectedFilter == LibraryFilter.PLAYLISTS,
                onClick = { onFilterSelected(LibraryFilter.PLAYLISTS) }
            )
        }
        item {
            FilterChip(
                text = "Artists",
                selected = selectedFilter == LibraryFilter.ARTISTS,
                onClick = { onFilterSelected(LibraryFilter.ARTISTS) }
            )
        }
        item {
            FilterChip(
                text = "Albums",
                selected = selectedFilter == LibraryFilter.ALBUMS,
                onClick = { onFilterSelected(LibraryFilter.ALBUMS) }
            )
        }
        item {
            FilterChip(
                text = "Songs",
                selected = selectedFilter == LibraryFilter.SONGS,
                onClick = { onFilterSelected(LibraryFilter.SONGS) }
            )
        }
    }
}

@Composable
private fun SongsContent(
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    onDeleteClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        items(tracks) { track ->
            TrackListItem(
                track = track,
                onClick = { onTrackClick(track) },
                onDeleteClick = { onDeleteClick(it) }
            )
        }
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums) { album ->
            AlbumGridItem(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = album.artworkUri,
            contentDescription = album.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistsGrid(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists) { artist ->
            ArtistGridItem(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun ArtistGridItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape),
            color = SpotifySurfaceVariant
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = artist.name,
                modifier = Modifier.padding(32.dp),
                tint = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun PlaylistsContent(
    playlists: List<Playlist>,
    likedSongsCount: Int,
    downloadedSongsCount: Int,
    onPlaylistClick: (Playlist) -> Unit,
    onLikedSongsClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onDeletePlaylistClick: (Playlist) -> Unit,
    onRenamePlaylistClick: (Playlist, String) -> Unit,
    onMovePlaylistUp: (Playlist) -> Unit,
    onMovePlaylistDown: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playlists.isEmpty()) {
        // Empty state
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Create your first playlist",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "It's easy, we'll help you",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onCreatePlaylistClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    contentColor = SpotifyBlack
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Create playlist")
            }
        }
    } else {
        // Playlist list
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Special Items
            item {
                SpecialPlaylistItem(
                    title = "Liked Songs",
                    subtitle = "$likedSongsCount songs",
                    icon = Icons.Default.Favorite,
                    color = Color(0xFF450AF5),
                    onClick = onLikedSongsClick
                )
            }
            item {
                SpecialPlaylistItem(
                    title = "Downloads",
                    subtitle = "$downloadedSongsCount songs",
                    icon = Icons.Default.Download,
                    color = Color(0xFF006450), // Teal-ish
                    onClick = onDownloadsClick
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onDeleteClick = { onDeletePlaylistClick(playlist) },
                    onRenameClick = { newName -> onRenamePlaylistClick(playlist, newName) },
                    onMoveUpClick = { onMovePlaylistUp(playlist) },
                    onMoveDownClick = { onMovePlaylistDown(playlist) }
                )
            }
        }
    }
}

@Composable
private fun SpecialPlaylistItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: (String) -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(playlist.name) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Playlist icon
        Surface(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = SpotifySurfaceVariant
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} songs",
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
                    onClick = {
                        showMenu = false
                        newName = playlist.name
                        showRenameDialog = true
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move up") },
                    onClick = {
                        showMenu = false
                        onMoveUpClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    onClick = {
                        showMenu = false
                        onMoveDownClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete playlist") },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                )
            }
        }
    }
    
    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRenameClick(newName)
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    focusedLabelColor = SpotifyGreen,
                    cursorColor = SpotifyGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (playlistName.isNotBlank()) {
                        onConfirm(playlistName.trim())
                    }
                },
                enabled = playlistName.isNotBlank()
            ) {
                Text("Create", color = if (playlistName.isNotBlank()) SpotifyGreen else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
