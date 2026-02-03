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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.YouTubeMetadata
import com.audioflow.player.ui.components.*
import com.audioflow.player.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateToPlayer: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val focusManager = LocalFocusManager.current
    
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
            
            // Search Bar
            TextField(
                value = uiState.query,
                onValueChange = { viewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = {
                    Text(
                        text = "What do you want to listen to?",
                        color = TextTertiary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
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
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Results
            when {
                uiState.isSearching || uiState.isYouTubeLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
                uiState.youtubeMetadata != null -> {
                    YouTubeResultContent(
                        metadata = uiState.youtubeMetadata!!,
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.youtubeError != null -> {
                    YouTubeErrorContent(
                        error = uiState.youtubeError!!,
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.hasResults -> {
                    SearchResultsContent(
                        uiState = uiState,
                        onTrackClick = { viewModel.playTrack(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                uiState.query.isNotEmpty() -> {
                    NoResultsContent(
                        query = uiState.query,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    BrowseContent(
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
                modifier = Modifier.padding(bottom = 80.dp)
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
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
private fun YouTubeResultContent(
    metadata: YouTubeMetadata,
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
        
        // Warning card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SpotifySurfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SpotifyGreen
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Direct playback is not available due to YouTube restrictions. You can open this video in the YouTube app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/watch?v=${metadata.videoId}")
                )
                context.startActivity(intent)
            },
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
            Text("Open in YouTube")
        }
    }
}

@Composable
private fun YouTubeErrorContent(
    error: String,
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
            text = "Invalid YouTube link",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun NoResultsContent(
    query: String,
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
            text = "Try searching for something else, or paste a YouTube link",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun BrowseContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "Browse all",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Search for songs, artists, or albums.\nYou can also paste a YouTube link to get video info.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
