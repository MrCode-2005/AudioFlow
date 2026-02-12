package com.audioflow.player.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.audioflow.player.data.local.entity.DownloadFolderEntity
import com.audioflow.player.ui.theme.SpotifyBlack
import com.audioflow.player.ui.theme.SpotifyGreen
import com.audioflow.player.ui.theme.TextPrimary
import com.audioflow.player.ui.theme.TextSecondary

/**
 * Bottom sheet dialog for moving a downloaded song into a folder.
 * Supports selecting existing folders or creating a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToDialog(
    folders: List<DownloadFolderEntity>,
    onMoveToFolder: (folderId: String) -> Unit,
    onMoveToRoot: () -> Unit,
    onCreateAndMove: (folderName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Move To",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // "No Folder (Root)" option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMoveToRoot() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Downloads (root)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }
            
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            // Existing folders
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMoveToFolder(folder.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
            
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            // Create new folder option
            if (showCreateField) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        placeholder = { Text("Folder name", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen,
                            cursorColor = SpotifyGreen,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                onCreateAndMove(newFolderName.trim())
                            }
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Text("Create & Move", color = if (newFolderName.isNotBlank()) SpotifyGreen else TextSecondary)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateField = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Create New Folder",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SpotifyGreen
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
