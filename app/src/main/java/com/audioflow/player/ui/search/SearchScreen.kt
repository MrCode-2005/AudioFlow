package com.audioflow.player.ui.search

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.data.local.RecentlyPlayedSong
import com.audioflow.player.data.remote.YouTubeSearchResult
import com.audioflow.player.model.YouTubeMetadata
import com.audioflow.player.ui.components.*
import com.audioflow.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToPlayer: () -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val recentlyPlayedSongs by viewModel.recentlyPlayedSongs.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // Auto-search if initialQuery is provided (e.g., from Go to Artist/Album)
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.updateQuery(initialQuery)
            viewModel.forceSearch(initialQuery)
        }
    }
    
    // Auto-dismiss keyboard when results load
    LaunchedEffect(uiState.shouldDismissKeyboard) {
        if (uiState.shouldDismissKeyboard) {
            focusManager.clearFocus()
            viewModel.onKeyboardDismissed()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Header
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                modifier = Modifier.padding(16.dp)
            )
            
            // Search Mode Toggle
            SearchModeToggle(
                currentMode = uiState.searchMode,
                onModeChange = { viewModel.setSearchMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Search Bar
            TextField(
                value = uiState.query,
                onValueChange = { viewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        text = when (uiState.searchMode) {
                            SearchMode.LOCAL -> "Search your music library"
                            SearchMode.YOUTUBE -> "Search YouTube for songs"
                        },
                        color = TextTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = when (uiState.searchMode) {
                            SearchMode.LOCAL -> Icons.Default.Search
                            SearchMode.YOUTUBE -> Icons.Default.PlayCircle
                        },
                        contentDescription = "Search",
                        tint = SpotifyBlack
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = SpotifyBlack
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TextPrimary,
                    unfocusedContainerColor = TextPrimary,
                    focusedTextColor = SpotifyBlack,
                    unfocusedTextColor = SpotifyBlack,
                    cursorColor = SpotifyBlack,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { 
                    viewModel.forceSearch(uiState.query)
                    focusManager.clearFocus() 
                })
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Content filter chips (only show for YouTube mode with results or a query)
            if (uiState.searchMode == SearchMode.YOUTUBE && uiState.query.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContentFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.contentFilter == filter,
                            onClick = { viewModel.setContentFilter(filter) },
                            label = { 
                                Text(
                                    text = when (filter) {
                                        ContentFilter.ALL -> "All"
                                        ContentFilter.SONGS -> "Songs"
                                        ContentFilter.PLAYLISTS -> "Playlists"
                                        ContentFilter.PODCASTS -> "Podcasts"
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = SpotifyBlack
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Content based on state
            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isSearching || uiState.isYouTubeLoading -> {
                        LoadingContent()
                    }
                    uiState.isExtractingStream -> {
                        ExtractingStreamContent()
                    }
                    uiState.youtubeMetadata != null -> {
                        YouTubeMetadataContent(
                            metadata = uiState.youtubeMetadata!!,
                            onPlayClick = { 
                                viewModel.playYouTubeResult(
                                    YouTubeSearchResult(
                                        videoId = uiState.youtubeMetadata!!.videoId,
                                        title = uiState.youtubeMetadata!!.title,
                                        artist = uiState.youtubeMetadata!!.author,
                                        thumbnailUrl = uiState.youtubeMetadata!!.thumbnailUrl,
                                        duration = uiState.youtubeMetadata!!.duration ?: 0
                                    )
                                )
                            }
                        )
                    }
                    uiState.youtubeError != null -> {
                        YouTubeErrorContent(
                            error = uiState.youtubeError!!,
                            onDismiss = { viewModel.clearError() }
                        )
                    }
                    uiState.searchMode == SearchMode.YOUTUBE && uiState.filteredResults.isNotEmpty() -> {
                        YouTubeSearchResultsContent(
                            results = uiState.filteredResults,
                            onResultClick = { viewModel.playYouTubeResult(it) }
                        )
                    }
                    uiState.searchMode == SearchMode.LOCAL && uiState.hasResults -> {
                        LocalSearchResultsContent(
                            uiState = uiState,
                            onTrackClick = { viewModel.playTrack(it) }
                        )
                    }
                    uiState.query.isNotEmpty() -> {
                        NoResultsContent(
                            query = uiState.query,
                            searchMode = uiState.searchMode
                        )
                    }
                    recentlyPlayedSongs.isNotEmpty() -> {
                        // Show recently played songs regardless of search mode when no query
                        RecentlyPlayedContent(
                            songs = recentlyPlayedSongs.take(20),
                            onSongClick = { song -> viewModel.playRecentlyPlayedSong(song) },
                            onSongDelete = { viewModel.removeRecentlyPlayedSong(it) },
                            onClearAll = { viewModel.clearAllRecentlyPlayed() }
                        )
                    }
                    else -> {
                        BrowseContent(searchMode = uiState.searchMode)
                    }
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
    }
}

@Composable
private fun SearchModeToggle(
    currentMode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchModeChip(
            label = "Library",
            icon = Icons.Default.LibraryMusic,
            isSelected = currentMode == SearchMode.LOCAL,
            onClick = { onModeChange(SearchMode.LOCAL) },
            modifier = Modifier.weight(1f)
        )
        SearchModeChip(
            label = "YouTube",
            icon = Icons.Default.PlayCircle,
            isSelected = currentMode == SearchMode.YOUTUBE,
            onClick = { onModeChange(SearchMode.YOUTUBE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) SpotifyGreen else SpotifySurfaceVariant,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) SpotifyBlack else TextSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) SpotifyBlack else TextSecondary
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = SpotifyGreen)
    }
}

@Composable
private fun ExtractingStreamContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = SpotifyGreen)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Preparing audio stream...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun YouTubeSearchResultsContent(
    results: List<YouTubeSearchResult>,
    onResultClick: (YouTubeSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            SectionHeader(title = "YouTube Results")
        }
        items(results) { result ->
            YouTubeResultItem(
                result = result,
                onClick = { onResultClick(result) }
            )
        }
    }
}

@Composable
private fun YouTubeResultItem(
    result: YouTubeSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with play icon overlay
        Box {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = result.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            // Play indicator overlay
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = ErrorRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = result.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.duration > 0) {
                    Text(
                        text = " • ${formatDuration(result.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LocalSearchResultsContent(
    uiState: SearchUiState,
    onTrackClick: (com.audioflow.player.model.Track) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Tracks
        if (uiState.tracks.isNotEmpty()) {
            item {
                SectionHeader(title = "Songs")
            }
            items(uiState.tracks.take(5)) { track ->
                TrackListItem(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
            }
        }
        
        // Artists
        if (uiState.artists.isNotEmpty()) {
            item {
                SectionHeader(title = "Artists")
            }
            items(uiState.artists.take(3)) { artist ->
                ArtistResultItem(artist = artist)
            }
        }
        
        // Albums
        if (uiState.albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Albums")
            }
            items(uiState.albums.take(3)) { album ->
                AlbumResultItem(album = album)
            }
        }
    }
}

@Composable
private fun ArtistResultItem(
    artist: com.audioflow.player.model.Artist
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to artist */ }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.CircleShape),
            color = SpotifySurfaceVariant
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "Artist",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AlbumResultItem(
    album: com.audioflow.player.model.Album
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate to album */ }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = album.artworkUri,
            contentDescription = album.name,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Album • ${album.artist}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun YouTubeMetadataContent(
    metadata: YouTubeMetadata,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Video thumbnail
        AsyncImage(
            model = metadata.thumbnailUrl,
            contentDescription = metadata.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = metadata.title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = metadata.author,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Play in app button
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpotifyGreen,
                contentColor = SpotifyBlack
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Play in AudioFlow")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Open in YouTube button
        OutlinedButton(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=${metadata.videoId}")
                )
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = TextPrimary
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(TextSecondary)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open in YouTube")
        }
    }
}

@Composable
private fun YouTubeErrorContent(
    error: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = ErrorRed
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        TextButton(onClick = onDismiss) {
            Text("Dismiss", color = SpotifyGreen)
        }
    }
}

@Composable
private fun NoResultsContent(
    query: String,
    searchMode: SearchMode,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = TextSecondary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No results found for \"$query\"",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = when (searchMode) {
                SearchMode.LOCAL -> "Try searching for something else, or switch to YouTube"
                SearchMode.YOUTUBE -> "Try a different search term"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun BrowseContent(
    searchMode: SearchMode,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = when (searchMode) {
                SearchMode.LOCAL -> "Search your library"
                SearchMode.YOUTUBE -> "Search YouTube"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when (searchMode) {
                SearchMode.LOCAL -> "Search for songs, artists, or albums from your device."
                SearchMode.YOUTUBE -> "Search for any song on YouTube and stream it directly in the app."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun SearchHistoryContent(
    history: List<String>,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Clear All",
                        color = SpotifyGreen
                    )
                }
            }
        }
        
        items(history) { query ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHistoryItemClick(query) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                IconButton(
                    onClick = { onHistoryItemDelete(query) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Format duration in milliseconds to mm:ss format
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Recently played songs content (Spotify-style song cards with thumbnails)
 */
@Composable
private fun RecentlyPlayedContent(
    songs: List<RecentlyPlayedSong>,
    onSongClick: (RecentlyPlayedSong) -> Unit,
    onSongDelete: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                TextButton(onClick = onClearAll) {
                    Text(
                        text = "Clear All",
                        color = SpotifyGreen
                    )
                }
            }
        }
        
        items(songs) { song ->
            RecentlyPlayedSongItem(
                song = song,
                onClick = { onSongClick(song) },
                onDelete = { onSongDelete(song.id) }
            )
        }
    }
}

@Composable
private fun RecentlyPlayedSongItem(
    song: RecentlyPlayedSong,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = song.thumbnailUri?.let { Uri.parse(it) },
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (song.duration > 0) {
                    Text(
                        text = " • ${formatDuration(song.duration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }
        
        // Add button (for adding to playlist)
        IconButton(
            onClick = { /* TODO: Add to playlist */ },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add to playlist",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Delete button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
