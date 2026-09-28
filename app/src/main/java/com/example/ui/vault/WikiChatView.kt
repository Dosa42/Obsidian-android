package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WikiChatView(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    selectedModel: GeminiModel,
    onModelSelected: (GeminiModel) -> Unit,
    onSendMessage: (String, Boolean) -> Unit,
    onSynthesizeTopic: (String) -> Unit,
    onWikilinkClicked: (String) -> Unit,
    onSaveToVault: (ChatMessage) -> Unit,
    onRunDiagnostic: () -> Unit = {},
    onOpenSkills: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var askVaultEnabled by remember { mutableStateOf(true) }
    var showSynthesizeDialog by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Top Venice Header & Model Bar
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianPurpleContainer)
                                .border(1.dp, ObsidianPurple, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Venice AI",
                                tint = ObsidianPurpleLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Venice Unfiltered",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ObsidianGreen.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianGreen)
                                ) {
                                    Text(
                                        text = "Root Tools Active",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianGreen,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Zero lecturing · Direct tool execution · Disk-backed",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )
                        }
                    }

                    // Model Selection Chip / Dropdown
                    Box {
                        Surface(
                            onClick = { showModelMenu = true },
                            color = ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                            modifier = Modifier.testTag("model_picker_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = selectedModel.displayName.replace("Gemini ", ""),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ObsidianTeal
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = ObsidianTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showModelMenu,
                            onDismissRequest = { showModelMenu = false },
                            modifier = Modifier.background(ObsidianSurfaceElevated)
                        ) {
                            GeminiModel.values().forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(model.displayName, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                            Text(model.description, fontSize = 10.sp, color = ObsidianTextMuted)
                                        }
                                    },
                                    onClick = {
                                        onModelSelected(model)
                                        showModelMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Horizontally Scrollable Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = askVaultEnabled,
                        onClick = { askVaultEnabled = !askVaultEnabled },
                        label = { Text("Ask Vault (RAG)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (askVaultEnabled) ObsidianYellow else ObsidianTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ObsidianPurpleContainer,
                            selectedLabelColor = ObsidianPurpleLight,
                            containerColor = ObsidianSurfaceElevated,
                            labelColor = ObsidianTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (askVaultEnabled) ObsidianPurple else ObsidianBorder,
                            enabled = true,
                            selected = askVaultEnabled
                        ),
                        modifier = Modifier.height(28.dp).testTag("ask_vault_toggle")
                    )

                    OutlinedButton(
                        onClick = { showSynthesizeDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianTextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("synthesize_wiki_button")
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Synthesize Node", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onRunDiagnostic,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianTeal
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("run_diagnostic_button")
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = ObsidianTeal, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Storage Audit", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenSkills,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianGreen
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("open_android_skills_button")
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = ObsidianGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Android Skills", fontSize = 11.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        // Chat Message Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    onWikilinkClicked = onWikilinkClicked,
                    onSaveToVault = onSaveToVault
                )
            }

            if (isLoading) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianPurpleLight,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Venice AI processing & executing system tools...",
                            style = MaterialTheme.typography.bodySmall.copy(color = ObsidianTextMuted)
                        )
                    }
                }
            }
        }

        // Tool Quick Actions Bar
        Surface(
            color = ObsidianSurfaceElevated,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Tools:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = ObsidianTextMuted
                )
                QuickToolPill("⚡ Telemetry") {
                    onSendMessage("Execute tool_call: get_device_telemetry and give me a brief summary.", false)
                }
                QuickToolPill("🗄️ Storage Audit") {
                    onSendMessage("Audit the Obsidian Vault filesystem and database health.", false)
                }
                QuickToolPill("📱 Sensors") {
                    onSendMessage("Execute tool_call: get_hardware_sensors to inspect phone hardware.", false)
                }
                QuickToolPill("📊 JVM Heap") {
                    onSendMessage("Execute tool_call: get_runtime_jvm to inspect thread count and memory.", false)
                }
                QuickToolPill("📐 Display Metrics") {
                    onSendMessage("Execute tool_call: get_display_metrics to inspect screen DPI and size.", false)
                }
                QuickToolPill("📎 Read Clipboard") {
                    onSendMessage("Execute tool_call: clipboard_read to read the current system clipboard text.", false)
                }
                QuickToolPill("📚 Android Architecture") {
                    onSendMessage("Query android knowledge for scoped_storage and explain it concisely.", false)
                }
                QuickToolPill("📝 Create Note") {
                    onSendMessage("Create a new note titled 'Cognitive Architecture' in folder 'Concepts' with relevant wikilinks and tags.", false)
                }
                QuickToolPill("🗺️ Generate MOC") {
                    onSendMessage("Execute dynamic script generate_moc_index to create a Map of Content.", false)
                }
                QuickToolPill("✨ Auto-Tag") {
                    onSendMessage("Execute dynamic script auto_tagger to organize meeting and action notes.", false)
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        // Bottom Input Bar
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = if (askVaultEnabled) "Ask vault with RAG or command tools..." else "Command Venice AI (tool calls enabled)...",
                            color = ObsidianTextMuted,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianSurfaceElevated,
                        unfocusedContainerColor = ObsidianSurfaceElevated,
                        focusedBorderColor = ObsidianPurple,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = ObsidianTextPrimary,
                        unfocusedTextColor = ObsidianTextPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText, askVaultEnabled)
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank() && !isLoading) ObsidianPurple else ObsidianSurfaceElevated)
                        .testTag("send_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank() && !isLoading) Color.White else ObsidianTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Dialog: Synthesize Wiki Node
    if (showSynthesizeDialog) {
        var topicInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSynthesizeDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (topicInput.isNotBlank()) {
                            onSynthesizeTopic(topicInput)
                            showSynthesizeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                    modifier = Modifier.testTag("confirm_synthesize_button")
                ) {
                    Text("Synthesize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSynthesizeDialog = false }) {
                    Text("Cancel", color = ObsidianTextSecondary)
                }
            },
            title = { Text("Synthesize Wiki Node") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Venice AI will analyze your vault and synthesize an authoritative wiki node with [[wikilinks]].",
                        fontSize = 12.sp,
                        color = ObsidianTextSecondary
                    )
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("Topic / Entity Name") },
                        placeholder = { Text("e.g. Distributed Consensus, Epistemology") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ObsidianPurple,
                            unfocusedBorderColor = ObsidianBorder,
                            focusedTextColor = ObsidianTextPrimary,
                            unfocusedTextColor = ObsidianTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("synthesize_topic_input")
                    )
                }
            },
            containerColor = ObsidianSurfaceElevated,
            titleContentColor = ObsidianTextPrimary
        )
    }
}

@Composable
private fun QuickToolPill(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = ObsidianSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, ObsidianBorder)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = ObsidianTextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onWikilinkClicked: (String) -> Unit,
    onSaveToVault: (ChatMessage) -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ObsidianPurpleContainer)
                    .border(1.dp, ObsidianPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Venice",
                    tint = ObsidianPurpleLight,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) ObsidianPurple else ObsidianSurfaceElevated
                ),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 12.dp
                ),
                border = if (!isUser) CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
                ) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (isUser) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                lineHeight = 20.sp
                            )
                        )
                    } else {
                        MarkdownRenderer(
                            content = message.text,
                            onWikilinkClicked = onWikilinkClicked
                        )
                    }
                }
            }

            // Executed System Developer Tools & Android Skills
            if (message.executedTools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.executedTools.forEach { tool ->
                        Surface(
                            color = ObsidianBackground,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (tool.success) ObsidianPurple else Color(0xFFE06C75)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (tool.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                            contentDescription = null,
                                            tint = if (tool.success) ObsidianGreen else Color(0xFFE06C75),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = tool.action.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ObsidianPurpleLight
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tool.target,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ObsidianTextPrimary,
                                            maxLines = 1
                                        )
                                    }

                                    if (tool.action in listOf("create_note", "update_note")) {
                                        TextButton(
                                            onClick = { onWikilinkClicked(tool.target) },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.height(20.dp)
                                        ) {
                                            Text("Open", fontSize = 10.sp, color = ObsidianTeal)
                                        }
                                    }
                                }

                                if (tool.details.isNotBlank() && tool.action != "run_diagnostic") {
                                    Text(
                                        text = tool.details,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = ObsidianTextSecondary,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cited Notes Badges (for model response)
            if (!isUser && message.citedNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text("Vault Context:", fontSize = 10.sp, color = ObsidianTextMuted)
                    message.citedNotes.take(3).forEach { title ->
                        Surface(
                            onClick = { onWikilinkClicked(title) },
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianBorder)
                        ) {
                            Text(
                                text = "[[$title]]",
                                fontSize = 10.sp,
                                color = ObsidianPurpleLight,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Save to Vault button for assistant response
            if (!isUser) {
                Spacer(modifier = Modifier.height(2.dp))
                TextButton(
                    onClick = { onSaveToVault(message) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).testTag("save_message_to_vault_button")
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = null, tint = ObsidianTeal, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Vault as Note", fontSize = 10.sp, color = ObsidianTeal)
                }
            }
        }
    }
}
