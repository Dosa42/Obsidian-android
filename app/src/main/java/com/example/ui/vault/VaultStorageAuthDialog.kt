package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.config.VaultAuthConfig
import com.example.data.skills.StorageAudit
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultStorageAuthDialog(
    authConfig: VaultAuthConfig,
    storageAudit: StorageAudit?,
    vaultPath: String,
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSavePersona: (name: String, title: String, prompt: String, temp: Double) -> Unit,
    onResyncStorage: () -> Unit,
    onExportBackupZip: () -> Unit,
    onClearChatHistory: () -> Unit,
    onExportChatMarkdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) } // 0 = Storage & DB, 1 = Auth & API, 2 = Venice Persona
    var inputApiKey by remember { mutableStateOf(authConfig.apiKey) }
    var personaName by remember { mutableStateOf(authConfig.personaName) }
    var personaTitle by remember { mutableStateOf(authConfig.personaTitle) }
    var personaPrompt by remember { mutableStateOf(authConfig.systemPrompt) }
    var personaTemp by remember { mutableStateOf(authConfig.temperature) }
    var showApiKey by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderShared,
                            contentDescription = null,
                            tint = ObsidianPurpleLight,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Vault Storage & Auth Hub",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )
                            Text(
                                text = "External Disk Persistence: /storage/emulated/0/Download",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_storage_auth_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                    }
                }

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight
                ) {
                    Tab(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        text = { Text("Storage & DB", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        text = { Text("Auth & Keys", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        text = { Text("Venice AI Persona", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            // Storage & Database Section
                            Text(
                                text = "Physical Storage Map (/storage/emulated/0/Download/ObsidianVault)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StoragePathItem(
                                        icon = Icons.Default.Folder,
                                        label = "Vault Root Directory",
                                        path = vaultPath,
                                        badge = "Public Storage",
                                        badgeColor = ObsidianTeal
                                    )
                                    Divider(color = ObsidianBorder)
                                    StoragePathItem(
                                        icon = Icons.Default.TableChart,
                                        label = "Room SQLite Database",
                                        path = "$vaultPath/.database/vault_storage.db",
                                        badge = "Active DB",
                                        badgeColor = ObsidianPurpleLight
                                    )
                                    Divider(color = ObsidianBorder)
                                    StoragePathItem(
                                        icon = Icons.Default.Security,
                                        label = "Auth & Persona Settings",
                                        path = "$vaultPath/.auth/vault_auth_config.json",
                                        badge = "Config JSON",
                                        badgeColor = ObsidianYellow
                                    )
                                    Divider(color = ObsidianBorder)
                                    StoragePathItem(
                                        icon = Icons.Default.Forum,
                                        label = "Chat History Persistence",
                                        path = "$vaultPath/.chat/chat_history.json",
                                        badge = "Chat Log",
                                        badgeColor = ObsidianGreen
                                    )
                                    Divider(color = ObsidianBorder)
                                    StoragePathItem(
                                        icon = Icons.Default.Code,
                                        label = "Dynamic Scripts & Rules",
                                        path = "$vaultPath/.scripts/",
                                        badge = "Hot-Reloaded",
                                        badgeColor = ObsidianTeal
                                    )
                                }
                            }

                            if (storageAudit != null) {
                                Text(
                                    text = "Storage Telemetry",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextPrimary
                                    )
                                )
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Indexed Vault Files:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${storageAudit.totalFiles} files", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Markdown Footprint:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${storageAudit.totalSizeBytes / 1024} KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Download Partition Free Space:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${storageAudit.freeSpaceBytes / (1024 * 1024 * 1024)} GB free", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianGreen)
                                        }
                                    }
                                }
                            }

                            // Storage Action Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onResyncStorage,
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("dialog_resync_button")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resync Disk", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = onExportBackupZip,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.weight(1f).testTag("dialog_backup_zip_button")
                                ) {
                                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ZIP Backup", fontSize = 12.sp)
                                }
                            }
                        }

                        1 -> {
                            // Auth & API Key Section
                            Text(
                                text = "Gemini API Key Configuration",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )
                            Text(
                                text = "Your API key is stored locally in `$vaultPath/.auth/vault_auth_config.json` on external storage.",
                                fontSize = 12.sp,
                                color = ObsidianTextSecondary
                            )

                            OutlinedTextField(
                                value = inputApiKey,
                                onValueChange = { inputApiKey = it },
                                label = { Text("GEMINI_API_KEY") },
                                placeholder = { Text("AIzaSy...") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Visibility",
                                            tint = ObsidianTextSecondary
                                        )
                                    }
                                },
                                visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dialog_api_key_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ObsidianPurpleLight,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianSurfaceElevated,
                                    unfocusedContainerColor = ObsidianSurfaceElevated,
                                    focusedTextColor = ObsidianTextPrimary,
                                    unfocusedTextColor = ObsidianTextPrimary
                                )
                            )

                            Button(
                                onClick = { onSaveApiKey(inputApiKey) },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("dialog_save_api_key_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Key to External Storage (.auth/)", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            Divider(color = ObsidianBorder)

                            Text(
                                text = "Conversation Management",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onExportChatMarkdown,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianPurpleLight),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPurple),
                                    modifier = Modifier.weight(1f).testTag("dialog_export_chat_button")
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Chat .md", fontSize = 11.sp)
                                }
                                OutlinedButton(
                                    onClick = onClearChatHistory,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianRed),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianRed),
                                    modifier = Modifier.weight(1f).testTag("dialog_clear_chat_button")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear History", fontSize = 11.sp)
                                }
                            }
                        }

                        2 -> {
                            // Venice AI Persona Configuration
                            Text(
                                text = "Venice AI Persona & System Instructions",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = personaName,
                                onValueChange = { personaName = it },
                                label = { Text("Persona Identifier") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ObsidianPurpleLight,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianSurfaceElevated,
                                    unfocusedContainerColor = ObsidianSurfaceElevated,
                                    focusedTextColor = ObsidianTextPrimary,
                                    unfocusedTextColor = ObsidianTextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = personaTitle,
                                onValueChange = { personaTitle = it },
                                label = { Text("Tagline / Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ObsidianPurpleLight,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianSurfaceElevated,
                                    unfocusedContainerColor = ObsidianSurfaceElevated,
                                    focusedTextColor = ObsidianTextPrimary,
                                    unfocusedTextColor = ObsidianTextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = personaPrompt,
                                onValueChange = { personaPrompt = it },
                                label = { Text("System Instruction Prompt") },
                                minLines = 5,
                                maxLines = 10,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ObsidianPurpleLight,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedContainerColor = ObsidianSurfaceElevated,
                                    unfocusedContainerColor = ObsidianSurfaceElevated,
                                    focusedTextColor = ObsidianTextPrimary,
                                    unfocusedTextColor = ObsidianTextPrimary
                                )
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Creativity / Temperature:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                    Text(String.format("%.2f", personaTemp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTeal)
                                }
                                Slider(
                                    value = personaTemp.toFloat(),
                                    onValueChange = { personaTemp = it.toDouble() },
                                    valueRange = 0.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ObsidianTeal,
                                        activeTrackColor = ObsidianPurpleLight
                                    )
                                )
                            }

                            Button(
                                onClick = {
                                    onSavePersona(personaName, personaTitle, personaPrompt, personaTemp)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("dialog_save_persona_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Persona to .auth/vault_auth_config.json", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Footer Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurfaceElevated)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Done", color = ObsidianTextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePathItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    path: String,
    badge: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianTextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = path,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = ObsidianTextSecondary,
                lineHeight = 14.sp
            )
        }
    }
}
