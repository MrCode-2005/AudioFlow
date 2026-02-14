package com.audioflow.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.audioflow.player.model.Track
import com.audioflow.player.ui.theme.SpotifyBlack
import com.audioflow.player.ui.theme.SpotifyGreen
import com.audioflow.player.ui.theme.SpotifySurfaceVariant
import com.audioflow.player.ui.theme.TextPrimary
import com.audioflow.player.ui.theme.TextSecondary
import com.audioflow.player.ui.search.SearchViewModel
import com.audioflow.player.ui.search.SearchMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToSectionSheet(
    onDismiss: () -> Unit,
    onAddTrack: (Track) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    
    // Ensure we start in YouTube mode for adding new songs
    LaunchedEffect(Unit) {
        if (uiState.searchMode != SearchMode.YOUTUBE) {
            searchViewModel.setSearchMode(SearchMode.YOUTUBE)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SpotifyBlack,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxHeight(0.9f) // Tall sheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Search Bar
            TextField(
                value = uiState.query,
                onValueChange = { searchViewModel.updateQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search YouTube...", color = TextSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { searchViewModel.clearSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SpotifySurfaceVariant,
                    unfocusedContainerColor = SpotifySurfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    cursorColor = SpotifyGreen
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { 
                    searchViewModel.forceSearch(uiState.query)
                    focusManager.clearFocus()
                })
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results
            if (uiState.isSearching || uiState.isYouTubeLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            } else if (uiState.searchMode == SearchMode.YOUTUBE && uiState.filteredResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredResults) { result ->
                        val track = Track(
                            id = "yt_${result.videoId}",
                            title = result.title,
                            artist = result.artist,
                            album = "YouTube",
                            artworkUri = android.net.Uri.parse(result.thumbnailUrl),
                            contentUri = android.net.Uri.EMPTY,
                            duration = result.duration,
                            source = com.audioflow.player.model.TrackSource.YOUTUBE
                        )
                        
                        AddSongItem(
                            track = track,
                            onAdd = { onAddTrack(track) }
                        )
                    }
                }
            } else if (uiState.query.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found",
                        color = TextSecondary
                    )
                }
            } else {
                // Initial State
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Search for songs to add", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AddSongItem(
    track: Track,
    onAdd: () -> Unit
) {
    var isAdded by remember { mutableStateOf(false) } // Local state for visual feedback

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(
            onClick = {
                onAdd()
                isAdded = true
            }
        ) {
            Icon(
                imageVector = if (isAdded) Icons.Default.Check else Icons.Default.AddCircleOutline,
                contentDescription = "Add",
                tint = if (isAdded) SpotifyGreen else TextSecondary
            )
        }
    }
}
