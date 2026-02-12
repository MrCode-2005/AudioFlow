package com.audioflow.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audioflow.player.data.local.FilterPreferences
import com.audioflow.player.ui.theme.*

/**
 * Bottom sheet for selecting language and genre filters.
 * Chips toggle on/off and persist via FilterPreferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filterPreferences: FilterPreferences,
    onDismiss: () -> Unit
) {
    val selectedLanguages by filterPreferences.selectedLanguages.collectAsState()
    val selectedGenres by filterPreferences.selectedGenres.collectAsState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (filterPreferences.hasActiveFilters()) {
                    TextButton(onClick = { filterPreferences.clearAll() }) {
                        Text("Clear All", color = SpotifyGreen)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Language Section
            Text(
                text = "Language",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipGrid(
                items = FilterPreferences.AVAILABLE_LANGUAGES,
                selectedItems = selectedLanguages,
                onToggle = { filterPreferences.toggleLanguage(it) }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Genre Section
            Text(
                text = "Genre",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            FilterChipGrid(
                items = FilterPreferences.AVAILABLE_GENRES,
                selectedItems = selectedGenres,
                onToggle = { filterPreferences.toggleGenre(it) }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FilterChipGrid(
    items: List<String>,
    selectedItems: Set<String>,
    onToggle: (String) -> Unit
) {
    // Use FlowRow-like layout with wrapping
    val rows = items.chunked(4)
    rows.forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems.forEach { item ->
                val selected = selectedItems.contains(item)
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(item) },
                    label = { Text(item, style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF2A2A2A),
                        labelColor = TextSecondary,
                        selectedContainerColor = SpotifyGreen.copy(alpha = 0.3f),
                        selectedLabelColor = SpotifyGreen,
                        selectedLeadingIconColor = SpotifyGreen
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color(0xFF444444),
                        selectedBorderColor = SpotifyGreen.copy(alpha = 0.5f),
                        enabled = true,
                        selected = selected
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
    }
}
