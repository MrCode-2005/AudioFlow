package com.audioflow.player.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audioflow.player.data.remote.LyricLine
import com.audioflow.player.data.remote.LyricsResult
import kotlinx.coroutines.launch

// Spotify-like pink/magenta color for lyrics
val LyricsPink = Color(0xFFE91E63)
val LyricsPinkDark = Color(0xFFC2185B)
val LyricsPinkLight = Color(0xFFF48FB1)

/**
 * Full-screen lyrics view with pink gradient background
 * Auto-scrolls to current lyric line based on playback position
 */
@Composable
fun LyricsScreen(
    title: String,
    artist: String,
    lyrics: LyricsResult?,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    progress: Float,
    onNavigateBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onShareClick: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Swipe-down dismiss state
    val dragOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 150.dp.toPx() }
    
    // Calculate current line index from synced lyrics
    val currentLineIndex = remember(lyrics, currentPosition) {
        lyrics?.syncedLines?.let { lines ->
            lines.indexOfLast { it.timestampMs <= currentPosition }
                .coerceAtLeast(0)
        } ?: 0
    }
    
    // Auto-scroll to current line when it changes
    LaunchedEffect(currentLineIndex) {
        if (lyrics?.syncedLines != null && currentLineIndex >= 0) {
            coroutineScope.launch {
                // Scroll with some padding (show a few lines before)
                val targetIndex = (currentLineIndex - 2).coerceAtLeast(0)
                listState.animateScrollToItem(targetIndex)
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffset.value
                // Scale down slightly as you drag for a polished feel
                val progress = (dragOffset.value / dismissThresholdPx).coerceIn(0f, 1f)
                scaleX = 1f - (progress * 0.05f)
                scaleY = 1f - (progress * 0.05f)
                alpha = 1f - (progress * 0.3f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (dragOffset.value > dismissThresholdPx) {
                                // Animate offscreen and dismiss
                                dragOffset.animateTo(
                                    targetValue = size.height.toFloat(),
                                    animationSpec = tween(200)
                                )
                                onNavigateBack()
                            } else {
                                // Snap back
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(200)
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffset.animateTo(0f, tween(200))
                        }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        coroutineScope.launch {
                            // Only allow dragging downward
                            val newOffset = (dragOffset.value + dragAmount).coerceAtLeast(0f)
                            dragOffset.snapTo(newOffset)
                        }
                    }
                )
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        LyricsPinkDark,
                        LyricsPink,
                        LyricsPink
                    )
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Lyrics content (LazyColumn for synced scrolling)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                if (lyrics != null) {
                    val lines = lyrics.syncedLines ?: lyrics.plainText.lines()
                        .filter { it.isNotBlank() }
                        .mapIndexed { index, text -> LyricLine((index * 3000).toLong(), text) }
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isCurrentLine = index == currentLineIndex
                            val isPastLine = index < currentLineIndex
                            
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.Bold,
                                    lineHeight = 36.sp,
                                    fontSize = if (isCurrentLine) 26.sp else 22.sp
                                ),
                                color = when {
                                    isCurrentLine -> Color.White
                                    isPastLine -> Color.White.copy(alpha = 0.5f)
                                    else -> Color.White.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                } else {
                    // No lyrics
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No lyrics available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                // Share and more buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Progress bar
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatLyricsDuration(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = formatLyricsDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Play/Pause button centered
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Synced lyrics preview card for NowPlayingScreen
 * Shows animated current line
 */
@Composable
fun LyricsPreviewCard(
    lyrics: LyricsResult?,
    currentPosition: Long,
    onShowLyricsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLineIndex = remember(lyrics, currentPosition) {
        lyrics?.syncedLines?.let { lines ->
            lines.indexOfLast { it.timestampMs <= currentPosition }
                .coerceAtLeast(0)
        } ?: 0
    }
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = LyricsPink,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Lyrics preview",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (lyrics?.syncedLines != null) {
                // Show synced lyrics with current line highlighted
                val lines = lyrics.syncedLines!!
                val startIndex = (currentLineIndex - 1).coerceAtLeast(0)
                val endIndex = (currentLineIndex + 3).coerceAtMost(lines.size)
                
                lines.subList(startIndex, endIndex).forEachIndexed { offset, line ->
                    val isCurrentLine = startIndex + offset == currentLineIndex
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = if (isCurrentLine) 18.sp else 14.sp
                        ),
                        color = if (isCurrentLine) Color.White else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            } else if (lyrics != null) {
                // Plain lyrics preview
                lyrics.plainText.lines()
                    .filter { it.isNotBlank() }
                    .take(4)
                    .forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Show lyrics button
            Surface(
                onClick = onShowLyricsClick,
                color = Color.Black.copy(alpha = 0.2f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Show lyrics",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun formatLyricsDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
