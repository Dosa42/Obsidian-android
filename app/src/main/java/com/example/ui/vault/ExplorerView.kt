package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VaultNote
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerView(
    notes: List<VaultNote>,
    bookmarkedNotes: List<VaultNote>,
    allTags: List<String>,
    allFolders: List<String>,
    isSyncing: Boolean,
    syncMessage: String?,
    selectedTagFilter: String?,
    vaultPath: String = "",
    onNoteClick: (VaultNote) -> Unit,
    onCreateNote: (String, String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onToggleBookmark: (VaultNote) -> Unit,
    onSyncFilesystem: () -> Unit,
    onTagSelected: (String?) -> Unit,
    onOpenDirectoryPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showOnlyBookmarks by remember { mutableStateOf(false) }
    val collapsedFolders = remember { mutableStateMapOf<String, Boolean>() }

    // Sync rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rot")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val displayedNotes = remember(notes, showOnlyBookmarks, selectedTagFilter) {
        var list = if (showOnlyBookmarks) bookmarkedNotes else notes
        if (selectedTagFilter != null) {
            list = list.filter { it.tags.contains(selectedTagFilter) }
        }
        list
    }

    val groupedByFolder = remember(displayedNotes) {
        displayedNotes.groupBy { it.folder }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Vault status banner
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PERSISTENT VAULT STORAGE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    color = ObsidianTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${notes.size} Notes · ${allFolders.size} Folders",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = ObsidianTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (vaultPath.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = vaultPath,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = ObsidianTeal,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[Switch]",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPurpleLight,
                                    modifier = Modifier
                                        .clickable { onOpenDirectoryPicker() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Direct filesystem access · Survives APK reinstall",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ObsidianGreen,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    // Sync Filesystem Button
                    OutlinedButton(
                        onClick = onSyncFilesystem,
                        enabled = !isSyncing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianPurpleLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sync_vault_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Synchronize Filesystem",
                            modifier = Modifier
                                .size(18.dp)
                                .then(if (isSyncing) Modifier.rotate(rotation) else Modifier)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSyncing) "Syncing..." else "Sync",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Sync status toast
                if (syncMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = syncMessage,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianTeal,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons: New Note, New Folder, Choose Folder, Bookmarks Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showNewNoteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianPurple,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_note_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Note", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Note", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showNewFolderDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianTextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("new_folder_button")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", modifier = Modifier.size(16.dp))
                    }

                    OutlinedButton(
                        onClick = onOpenDirectoryPicker,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianPurpleLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPurple.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("open_directory_picker_button")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Choose Folder", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilterChip(
                        selected = showOnlyBookmarks,
                        onClick = { showOnlyBookmarks = !showOnlyBookmarks },
                        label = { Text("Starred (${bookmarkedNotes.size})", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showOnlyBookmarks) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Starred",
                                tint = if (showOnlyBookmarks) ObsidianYellow else ObsidianTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ObsidianPurpleContainer,
                            selectedLabelColor = ObsidianPurpleLight,
                            containerColor = ObsidianSurfaceElevated,
                            labelColor = ObsidianTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (showOnlyBookmarks) ObsidianPurple else ObsidianBorder,
                            enabled = true,
                            selected = showOnlyBookmarks
                        ),
                        modifier = Modifier.testTag("starred_filter_chip")
                    )
                }

                // Tags horizontal filter bar
                if (allTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedTagFilter == null,
                                onClick = { onTagSelected(null) },
                                label = { Text("All", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPurple,
                                    selectedLabelColor = Color.White,
                                    containerColor = ObsidianSurfaceElevated,
                                    labelColor = ObsidianTextSecondary
                                ),
                                border = null,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                        items(allTags) { tag ->
                            val isSelected = selectedTagFilter == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTagSelected(tag) },
                                label = { Text("#$tag", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPurpleContainer,
                                    selectedLabelColor = ObsidianPurpleLight,
                                    containerColor = ObsidianSurfaceElevated,
                                    labelColor = ObsidianTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) ObsidianPurple else ObsidianBorder,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        // Notes and Folders List
        if (displayedNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = "Empty",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (showOnlyBookmarks) "No starred notes in vault" else "Vault is empty",
                        color = ObsidianTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create a note or tap Sync to ingest filesystem",
                        color = ObsidianTextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedByFolder.forEach { (folder, folderNotes) ->
                    val isCollapsed = collapsedFolders[folder] ?: false

                    // Folder Header
                    item(key = "folder_$folder") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    collapsedFolders[folder] = !isCollapsed
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                contentDescription = "Toggle folder",
                                tint = ObsidianTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isCollapsed) Icons.Default.Folder else Icons.Default.FolderOpen,
                                contentDescription = "Folder",
                                tint = ObsidianPurpleLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folder,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = ObsidianTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${folderNotes.size})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ObsidianTextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Notes in this folder
                    if (!isCollapsed) {
                        items(folderNotes, key = { it.id }) { note ->
                            NoteListItem(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onToggleBookmark = { onToggleBookmark(note) }
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Dialog: Create Note
    if (showNewNoteDialog) {
        var noteTitle by remember { mutableStateOf("") }
        var selectedFolder by remember { mutableStateOf(allFolders.firstOrNull() ?: "Root") }

        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitle.isNotBlank()) {
                            onCreateNote(noteTitle, selectedFolder)
                            showNewNoteDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                    modifier = Modifier.testTag("confirm_create_note_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewNoteDialog = false }) {
                    Text("Cancel", color = ObsidianTextSecondary)
                }
            },
            title = { Text("Create New Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurple,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = ObsidianTextPrimary,
                            unfocusedTextColor = ObsidianTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )

                    Text("Destination Folder:", fontSize = 12.sp, color = ObsidianTextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (allFolders.take(4)).forEach { fld ->
                            FilterChip(
                                selected = selectedFolder == fld,
                                onClick = { selectedFolder = fld },
                                label = { Text(fld, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPurple,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            },
            containerColor = ObsidianSurfaceElevated,
            titleContentColor = ObsidianTextPrimary
        )
    }

    // Dialog: Create Folder
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName)
                            showNewFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                    modifier = Modifier.testTag("confirm_create_folder_button")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = ObsidianTextSecondary)
                }
            },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ObsidianPurple,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = ObsidianTextPrimary,
                        unfocusedTextColor = ObsidianTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )
            },
            containerColor = ObsidianSurfaceElevated,
            titleContentColor = ObsidianTextPrimary
        )
    }
}

@Composable
fun NoteListItem(
    note: VaultNote,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val formattedDate = remember(note.lastModified) { dateFormat.format(Date(note.lastModified)) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .testTag("note_item_${note.title.replace(" ", "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = "Note",
                tint = ObsidianPurpleLight,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = ObsidianTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianTextMuted,
                            fontSize = 11.sp
                        )
                    )
                    if (note.outlinks.isNotEmpty()) {
                        Text(
                            text = "${note.outlinks.size} links",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ObsidianTeal,
                                fontSize = 11.sp
                            )
                        )
                    }
                    if (note.tags.isNotEmpty()) {
                        Text(
                            text = note.tags.take(2).joinToString(" ") { "#$it" },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ObsidianYellow,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (note.isBookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (note.isBookmarked) "Unstar" else "Star",
                    tint = if (note.isBookmarked) ObsidianYellow else ObsidianTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
