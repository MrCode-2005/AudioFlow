package com.audioflow.player.ui.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.audioflow.player.ui.theme.*

/**
 * Create screen with animated FAB that reveals playlist creation options
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    var showCustomPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    
    // Animation for rotating the + button
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "rotation"
    )
    
    // Handle generation success - navigate to Library
    LaunchedEffect(uiState.navigateToLibrary) {
        if (uiState.navigateToLibrary && uiState.generatedPlaylistId != null) {
            isExpanded = false
            viewModel.clearGeneratedState()
            onNavigateToLibrary()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SpotifySurface,
                        SpotifyBlack
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "Create",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Build your perfect playlists",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Loading indicator
            if (uiState.isGenerating) {
                CircularProgressIndicator(color = SpotifyGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Generating playlist...", color = TextSecondary)
            } else {
                // Action buttons that appear when FAB is expanded
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Create Playlist option
                        CreateOptionButton(
                            icon = Icons.Default.PlaylistAdd,
                            title = "Create Playlist",
                            subtitle = "Start a new empty playlist",
                            onClick = {
                                showCreatePlaylistDialog = true
                                isExpanded = false
                            }
                        )
                        
                        // Custom Playlist option
                        CreateOptionButton(
                            icon = Icons.Default.AutoAwesome,
                            title = "Create Custom Playlist",
                            subtitle = "Generate playlist by artist & genre",
                            onClick = {
                                showCustomPlaylistDialog = true
                                isExpanded = false
                            }
                        )
                    }
                }
            }
            
            // Main FAB (hide when generating)
            if (!uiState.isGenerating) {
                FloatingActionButton(
                    onClick = { isExpanded = !isExpanded },
                    containerColor = SpotifyGreen,
                    contentColor = SpotifyBlack,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (isExpanded) "Close" else "Create",
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(rotation)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
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
        
        // Custom Playlist Dialog
        if (showCustomPlaylistDialog) {
            CustomPlaylistDialog(
                onDismiss = { showCustomPlaylistDialog = false },
                onConfirm = { artist, genre, customGenre, songCount ->
                    viewModel.generateCustomPlaylist(artist, genre, customGenre, songCount)
                    showCustomPlaylistDialog = false
                }
            )
        }
    }
}

@Composable
private fun CreateOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SpotifySurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                color = SpotifyGreen.copy(alpha = 0.2f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
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
        containerColor = SpotifySurface,
        title = {
            Text(
                text = "Create Playlist",
                color = TextPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = { 
                    Text("My Playlist", color = TextTertiary) 
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = TextSecondary,
                    cursorColor = SpotifyGreen
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(playlistName) },
                enabled = playlistName.isNotBlank()
            ) {
                Text("Create", color = SpotifyGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (artist: String, genre: String, customGenre: String, songCount: Int) -> Unit
) {
    var artistName by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("") }
    var customGenre by remember { mutableStateOf("") }
    var songCount by remember { mutableFloatStateOf(15f) }
    
    val genres = listOf(
        "Love Songs",
        "Calm & Relaxing",
        "Energetic",
        "Party Hits",
        "Workout",
        "Focus",
        "Chill",
        "Sad Songs"
    )
    
    // At least one of artist, selectedGenre, or customGenre must be filled
    val isValid = artistName.isNotBlank() || selectedGenre.isNotBlank() || customGenre.isNotBlank()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpotifySurface,
        title = {
            Text(
                text = "Create Custom Playlist",
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Fill in any field to generate a personalized playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                // Artist Name
                OutlinedTextField(
                    value = artistName,
                    onValueChange = { artistName = it },
                    label = { Text("Artist Name (optional)", color = TextSecondary) },
                    placeholder = { Text("e.g., Taylor Swift", color = TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = SpotifyGreen,
                        focusedLabelColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Custom Genre Text Input
                OutlinedTextField(
                    value = customGenre,
                    onValueChange = { customGenre = it },
                    label = { Text("Custom Genre (optional)", color = TextSecondary) },
                    placeholder = { Text("e.g., 90s Hip Hop", color = TextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = SpotifyGreen,
                        focusedLabelColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Or Select a Vibe",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                
                // Genre chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genres.forEach { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { 
                                selectedGenre = if (selectedGenre == genre) "" else genre 
                            },
                            label = { Text(genre) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = SpotifyBlack
                            )
                        )
                    }
                }
                
                // Song Count Slider
                Text(
                    text = "Number of Songs: ${songCount.toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Slider(
                    value = songCount,
                    onValueChange = { songCount = it },
                    valueRange = 5f..30f,
                    steps = 24,
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(artistName, selectedGenre, customGenre, songCount.toInt()) },
                enabled = isValid
            ) {
                Text("Generate", color = if (isValid) SpotifyGreen else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
