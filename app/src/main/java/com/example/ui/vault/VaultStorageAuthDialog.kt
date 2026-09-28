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
import com.example.data.auth.ChatGPTSession
import com.example.data.config.VaultAuthConfig
import com.example.data.skills.StorageAudit
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultStorageAuthDialog(
    authConfig: VaultAuthConfig,
    storageAudit: StorageAudit?,
    vaultPath: String,
    hardwareState: com.example.data.adaptive.HardwareContextState? = null,
    chatGPTSession: ChatGPTSession? = null,
    activeProvider: String = "gemini",
    onSelectProvider: (String) -> Unit = {},
    onInitiateChatGPTLogin: () -> Unit = {},
    onCompleteChatGPTLogin: (String) -> Unit = {},
    onSignOutOfChatGPT: () -> Unit = {},
    onDismiss: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onSavePersona: (name: String, title: String, prompt: String, temp: Double) -> Unit,
    onResyncStorage: () -> Unit,
    onExportBackupZip: () -> Unit,
    onClearChatHistory: () -> Unit,
    onExportChatMarkdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(0) } // 0 = Storage & DB, 1 = Auth & Keys, 2 = Venice Persona, 3 = ChatGPT PKCE
    var inputApiKey by remember { mutableStateOf(authConfig.apiKey) }
    var personaName by remember { mutableStateOf(authConfig.personaName) }
    var personaTitle by remember { mutableStateOf(authConfig.personaTitle) }
    var personaPrompt by remember { mutableStateOf(authConfig.systemPrompt) }
    var personaTemp by remember { mutableStateOf(authConfig.temperature) }
    var showApiKey by remember { mutableStateOf(false) }
    var callbackUrlInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
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
                                text = "External Disk: /storage/emulated/0/Download",
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
                ScrollableTabRow(
                    selectedTabIndex = selectedSection,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight,
                    edgePadding = 8.dp
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
                        text = { Text("Gemini API", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 2,
                        onClick = { selectedSection = 2 },
                        text = { Text("Venice AI", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedSection == 3,
                        onClick = { selectedSection = 3 },
                        text = { Text("ChatGPT PKCE", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StoragePathRow("📁 Markdown Notes:", "$vaultPath/")
                                    StoragePathRow("🗄️ SQLite Database:", "$vaultPath/.database/vault_storage.db")
                                    StoragePathRow("🔐 Auth Config:", "$vaultPath/.auth/vault_auth_config.json")
                                    StoragePathRow("🔑 ChatGPT Session:", "$vaultPath/.auth/chatgpt_session.json")
                                    StoragePathRow("💬 Chat Persistence:", "$vaultPath/.chat/chat_history.json")
                                    StoragePathRow("📜 Dynamic Scripts:", "$vaultPath/.scripts/")
                                }
                            }

                            if (storageAudit != null) {
                                Text(
                                    text = "External Disk Partition Telemetry",
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
                                            Text("Indexed Notes Count:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${storageAudit.totalFiles} files", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Vault Size on Disk:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${storageAudit.totalSizeBytes / 1024} KB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianPurpleLight)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("External Free Storage:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${String.format("%.2f", storageAudit.freeSpaceBytes.toDouble() / (1024 * 1024 * 1024))} GB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianGreen)
                                        }
                                    }
                                }
                            }

                            if (hardwareState != null) {
                                Text(
                                    text = "Adaptive Hardware & Power State",
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
                                            Text("Hardware Tier:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text(hardwareState.hardwareTier.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianPurpleLight)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Battery & Power:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${hardwareState.batteryPercent}% (${if (hardwareState.isCharging) "Charging ⚡" else "On Battery"})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (hardwareState.isCharging) ObsidianGreen else ObsidianYellow)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Network Connection:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text(hardwareState.networkTier.name.replace("_", " "), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTeal)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Dynamic Context Budget:", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Text("${hardwareState.dynamicContextBudget} tokens", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
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
                            // Gemini API Key Section
                            Text(
                                text = "Gemini API Configuration",
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
                        }

                        2 -> {
                            // Venice AI Persona Section
                            Text(
                                text = "Venice AI Coprocessor Persona",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )

                            OutlinedTextField(
                                value = personaName,
                                onValueChange = { personaName = it },
                                label = { Text("Persona Identifier") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = personaTitle,
                                onValueChange = { personaTitle = it },
                                label = { Text("Subtitle / Tagline") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = personaPrompt,
                                onValueChange = { personaPrompt = it },
                                label = { Text("System Instructions") },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = { onSavePersona(personaName, personaTitle, personaPrompt, personaTemp) },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurpleLight),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Persona Configuration", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        3 -> {
                            // ChatGPT PKCE OAuth Section
                            Text(
                                text = "ChatGPT OAuth 2.0 PKCE Integration",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )
                            Text(
                                text = "Official authorization code flow with PKCE (SHA-256) and local loopback listener on port 1455. Direct connection to ChatGPT Codex without requiring an API key.",
                                fontSize = 12.sp,
                                color = ObsidianTextSecondary
                            )

                            // Active Provider Selector
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Active AI Coprocessor Provider:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = activeProvider == "gemini",
                                            onClick = { onSelectProvider("gemini") },
                                            label = { Text("Gemini AI API") },
                                            leadingIcon = if (activeProvider == "gemini") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null
                                        )
                                        FilterChip(
                                            selected = activeProvider == "chatgpt",
                                            onClick = { onSelectProvider("chatgpt") },
                                            label = { Text("ChatGPT OAuth PKCE") },
                                            leadingIcon = if (activeProvider == "chatgpt") { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) } } else null
                                        )
                                    }
                                }
                            }

                            if (chatGPTSession != null && chatGPTSession.isValid) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianGreen)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianGreen, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Authenticated with ChatGPT Codex", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ObsidianGreen)
                                        }
                                        Text("Account Email: ${chatGPTSession.email}", fontSize = 12.sp, color = ObsidianTextPrimary)
                                        if (chatGPTSession.accountId.isNotBlank()) {
                                            Text("Account ID: ${chatGPTSession.accountId}", fontSize = 11.sp, color = ObsidianTextSecondary)
                                        }
                                        val expiryStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(chatGPTSession.expiresAt))
                                        Text("Token Expiry: $expiryStr", fontSize = 11.sp, color = ObsidianTextMuted)

                                        Button(
                                            onClick = onSignOutOfChatGPT,
                                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianRed),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                        ) {
                                            Text("Sign Out from ChatGPT", fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onInitiateChatGPTLogin,
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianTeal),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign In with ChatGPT OAuth PKCE", fontSize = 13.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                Text(
                                    text = "Manual Callback Paste (Fallback):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextSecondary
                                )
                                OutlinedTextField(
                                    value = callbackUrlInput,
                                    onValueChange = { callbackUrlInput = it },
                                    label = { Text("Paste localhost:1455 callback URL") },
                                    placeholder = { Text("http://localhost:1455/auth/callback?code=...&state=...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Button(
                                    onClick = {
                                        if (callbackUrlInput.isNotBlank()) {
                                            onCompleteChatGPTLogin(callbackUrlInput.trim())
                                        }
                                    },
                                    enabled = callbackUrlInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Complete Manual Sign-In", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePathRow(label: String, path: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, fontSize = 11.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
        Text(
            text = path,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = ObsidianPurpleLight,
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianBackground, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
