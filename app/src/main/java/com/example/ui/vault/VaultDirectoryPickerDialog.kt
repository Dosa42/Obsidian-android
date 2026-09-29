package com.example.ui.vault

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDirectoryPickerDialog(
    currentVaultPath: String,
    onSelectDirectory: (String) -> Unit,
    onLaunchSafPicker: () -> Unit,
    onResetToDefault: () -> Unit,
    onBrowseDirectories: (String) -> List<File>,
    onCountNotes: (File) -> Int,
    commonDirectories: List<File>,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var manualPathInput by remember { mutableStateOf(currentVaultPath) }
    
    // In-app directory browser state
    var browserCurrentPath by remember { 
        val initial = if (File(currentVaultPath).exists()) {
            File(currentVaultPath).parentFile?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
        mutableStateOf(initial) 
    }
    var browserSubdirectories by remember(browserCurrentPath) {
        mutableStateOf(onBrowseDirectories(browserCurrentPath))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .testTag("vault_directory_picker_dialog"),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPurple.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianPurpleContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = ObsidianPurpleLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Choose Vault Directory",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )
                            Text(
                                text = "Select any folder from your Android filesystem",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_dir_picker_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                    }
                }

                // Currently active path banner
                Surface(
                    color = ObsidianSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active Vault Location:", fontSize = 10.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                text = currentVaultPath,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ObsidianTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onResetToDefault()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.testTag("reset_default_dir_button")
                        ) {
                            Text("Reset Default", fontSize = 10.sp, color = ObsidianTextSecondary)
                        }
                    }
                }

                // Tab Switcher
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight,
                    edgePadding = 8.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Android SAF Picker", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Device Browser", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Common Presets", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Custom Path", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Android Storage Access Framework (SAF) system picker
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianGreen, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Official Android System Directory Picker", fontWeight = FontWeight.Bold, color = ObsidianTextPrimary, fontSize = 13.sp)
                                        }
                                        Text(
                                            text = "Tapping the button below opens the native Android Files / Storage provider. You can navigate your entire phone, SD Card, Documents, Downloads, or existing Obsidian folders and select 'Use this folder'.",
                                            fontSize = 12.sp,
                                            color = ObsidianTextSecondary,
                                            lineHeight = 16.sp
                                        )
                                        Text(
                                            text = "• Seamless persistence across app restarts\n• Instant auto-indexing of all existing .md files\n• Full read & write synchronization",
                                            fontSize = 11.sp,
                                            color = ObsidianPurpleLight,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        onLaunchSafPicker()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("launch_saf_folder_picker_btn")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open System Folder Picker", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        1 -> {
                            // Interactive Device Browser
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Current browser path breadcrumb & up navigation
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ObsidianSurfaceElevated, RoundedCornerShape(8.dp))
                                        .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = {
                                            val parent = File(browserCurrentPath).parentFile
                                            if (parent != null && parent.exists()) {
                                                browserCurrentPath = parent.absolutePath
                                                browserSubdirectories = onBrowseDirectories(parent.absolutePath)
                                            }
                                        },
                                        enabled = File(browserCurrentPath).parentFile != null,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Go Up", tint = ObsidianTextPrimary)
                                    }

                                    Text(
                                        text = browserCurrentPath,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = ObsidianTeal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                    )

                                    Button(
                                        onClick = {
                                            onSelectDirectory(browserCurrentPath)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianGreen),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("select_browser_folder_btn")
                                    ) {
                                        Text("Select This Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianBackground)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (browserSubdirectories.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = ObsidianTextSecondary, modifier = Modifier.size(40.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("No subdirectories here", color = ObsidianTextSecondary, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    onSelectDirectory(browserCurrentPath)
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple)
                                            ) {
                                                Text("Use '$browserCurrentPath' as Vault", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(browserSubdirectories, key = { it.absolutePath }) { folder ->
                                            val noteCount = remember(folder.absolutePath) { onCountNotes(folder) }
                                            Surface(
                                                color = ObsidianSurfaceElevated,
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        browserCurrentPath = folder.absolutePath
                                                        browserSubdirectories = onBrowseDirectories(folder.absolutePath)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                        Icon(Icons.Default.Folder, contentDescription = null, tint = ObsidianYellow, modifier = Modifier.size(20.dp))
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = folder.name,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp,
                                                                color = ObsidianTextPrimary
                                                            )
                                                            if (noteCount > 0) {
                                                                Text("$noteCount markdown notes inside", fontSize = 10.sp, color = ObsidianPurpleLight)
                                                            }
                                                        }
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                onSelectDirectory(folder.absolutePath)
                                                                onDismiss()
                                                            },
                                                            shape = RoundedCornerShape(6.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPurple.copy(alpha = 0.5f))
                                                        ) {
                                                            Text("Choose", fontSize = 11.sp, color = ObsidianPurpleLight)
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ObsidianTextSecondary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Common Presets
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text("Popular & Standard Storage Locations", fontSize = 12.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                items(commonDirectories, key = { it.absolutePath }) { dir ->
                                    val isSelected = dir.absolutePath.equals(currentVaultPath, ignoreCase = true)
                                    val noteCount = remember(dir.absolutePath) { onCountNotes(dir) }

                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) ObsidianPurpleContainer else ObsidianSurfaceElevated
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) ObsidianPurple else ObsidianBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectDirectory(dir.absolutePath)
                                                onDismiss()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = if (isSelected) ObsidianGreen else ObsidianPurpleLight,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = dir.name.ifEmpty { "External Storage Root" },
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = ObsidianTextPrimary
                                                    )
                                                    Text(
                                                        text = dir.absolutePath,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = ObsidianTextSecondary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (noteCount > 0) {
                                                        Text("$noteCount notes found", fontSize = 10.sp, color = ObsidianGreen)
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    onSelectDirectory(dir.absolutePath)
                                                    onDismiss()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) ObsidianGreen else ObsidianPurple
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isSelected) "Active" else "Mount", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Custom Path Input
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Enter Exact Directory Path", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ObsidianTextPrimary)
                                Text(
                                    text = "Type or paste any existing path on your Android device (e.g. `/storage/emulated/0/Documents/ObsidianVault` or `/sdcard/MyNotes`).",
                                    fontSize = 12.sp,
                                    color = ObsidianTextSecondary
                                )

                                OutlinedTextField(
                                    value = manualPathInput,
                                    onValueChange = { manualPathInput = it },
                                    label = { Text("Absolute Directory Path") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("manual_dir_path_input"),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ObsidianPurple,
                                        unfocusedBorderColor = ObsidianBorder,
                                        focusedTextColor = ObsidianTextPrimary,
                                        unfocusedTextColor = ObsidianTextPrimary,
                                        focusedContainerColor = ObsidianSurfaceElevated,
                                        unfocusedContainerColor = ObsidianSurfaceElevated
                                    )
                                )

                                val targetFile = remember(manualPathInput) { File(manualPathInput.trim()) }
                                val exists = remember(manualPathInput) { targetFile.exists() }
                                val noteCount = remember(manualPathInput) { onCountNotes(targetFile) }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Path Inspection:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianTextSecondary)
                                        Text(
                                            text = if (exists) "✓ Path exists on filesystem" else "⚠️ Path does not exist yet (will be created automatically on mount)",
                                            fontSize = 11.sp,
                                            color = if (exists) ObsidianGreen else ObsidianYellow
                                        )
                                        if (exists && noteCount > 0) {
                                            Text("Found $noteCount existing Markdown notes to index.", fontSize = 11.sp, color = ObsidianPurpleLight)
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (manualPathInput.isNotBlank()) {
                                            onSelectDirectory(manualPathInput.trim())
                                            onDismiss()
                                        }
                                    },
                                    enabled = manualPathInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("apply_manual_path_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply & Mount Vault", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
