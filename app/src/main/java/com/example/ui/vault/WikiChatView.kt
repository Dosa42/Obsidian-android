package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.auth.ChatGPTModelInfo
import com.example.data.auth.ChatGPTSession
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
    activeProvider: String = "gemini",
    onSelectProvider: (String) -> Unit = {},
    selectedChatGPTModel: String = "gpt-4o",
    chatGPTModels: List<ChatGPTModelInfo> = emptyList(),
    isLoadingChatGPTModels: Boolean = false,
    chatGPTSession: ChatGPTSession? = null,
    onSelectChatGPTModel: (String) -> Unit = {},
    onRefreshChatGPTModels: () -> Unit = {},
    onAddCustomChatGPTModel: (String, String) -> Unit = { _, _ -> },
    onInitiateChatGPTLogin: () -> Unit = {},
    onSignOutOfChatGPT: () -> Unit = {},
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
    var showModelDialog by remember { mutableStateOf(false) }
    var modelDialogTab by remember { mutableIntStateOf(if (activeProvider == "chatgpt") 1 else 0) }
    var customModelInput by remember { mutableStateOf("") }
    var showAddCustomModel by remember { mutableStateOf(false) }
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
                                    color = if (activeProvider == "chatgpt") ObsidianGreen.copy(alpha = 0.2f) else ObsidianPurple.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp,
                                        if (activeProvider == "chatgpt") ObsidianGreen else ObsidianPurple
                                    )
                                ) {
                                    Text(
                                        text = if (activeProvider == "chatgpt") "ChatGPT Codex" else "Gemini RAG",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeProvider == "chatgpt") ObsidianGreen else ObsidianPurpleLight,
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

                    // Model Selection Chip / Button
                    Surface(
                        onClick = {
                            modelDialogTab = if (activeProvider == "chatgpt") 1 else 0
                            showModelDialog = true
                        },
                        color = ObsidianSurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (activeProvider == "chatgpt") ObsidianGreen else ObsidianPurpleLight
                        ),
                        modifier = Modifier.testTag("model_picker_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (activeProvider == "chatgpt") {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (chatGPTSession?.isValid == true) ObsidianGreen else ObsidianYellow)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val currentChatGPTName = chatGPTModels.find { it.id == selectedChatGPTModel }?.name
                                    ?: selectedChatGPTModel
                                Text(
                                    text = currentChatGPTName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianGreen
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ObsidianTeal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = selectedModel.displayName.replace("Gemini ", ""),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ObsidianTeal
                                )
                            }
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = ObsidianTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
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
                            contentColor = ObsidianTeal
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianTeal.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Synthesize Topic", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onOpenSkills,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ObsidianPurpleLight
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianPurple.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp).testTag("open_android_skills_btn")
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Android Root Skills", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Model & Provider Selection Modal Dialog
        if (showModelDialog) {
            Dialog(
                onDismissRequest = { showModelDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Dialog Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = ObsidianPurpleLight,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select AI Model & Provider",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextPrimary
                                    )
                                )
                            }
                            IconButton(onClick = { showModelDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Provider Tabs
                        PrimaryTabRow(
                            selectedTabIndex = modelDialogTab,
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = ObsidianPurpleLight,
                            indicator = {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(modelDialogTab),
                                    color = if (modelDialogTab == 0) ObsidianPurpleLight else ObsidianGreen
                                )
                            }
                        ) {
                            Tab(
                                selected = modelDialogTab == 0,
                                onClick = { modelDialogTab = 0 },
                                text = { Text("Google Gemini", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = ObsidianTeal) }
                            )
                            Tab(
                                selected = modelDialogTab == 1,
                                onClick = { modelDialogTab = 1 },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("OpenAI ChatGPT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        if (chatGPTSession?.isValid == true) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(ObsidianGreen)
                                            )
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp), tint = ObsidianGreen) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tab Content
                        if (modelDialogTab == 0) {
                            // Gemini Models List
                            Text(
                                text = "Native Gemini Models (Server & Key Backed)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GeminiModel.values().forEach { model ->
                                    val isSelected = activeProvider == "gemini" && selectedModel == model
                                    Surface(
                                        onClick = {
                                            onSelectProvider("gemini")
                                            onModelSelected(model)
                                            showModelDialog = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) ObsidianPurpleContainer else ObsidianSurfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) ObsidianPurpleLight else ObsidianBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("select_model_${model.name}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = {
                                                    onSelectProvider("gemini")
                                                    onModelSelected(model)
                                                    showModelDialog = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = model.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = ObsidianTextPrimary
                                                )
                                                Text(
                                                    text = model.description,
                                                    fontSize = 11.sp,
                                                    color = ObsidianTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ChatGPT Models List & OAuth Status
                            if (chatGPTSession != null && chatGPTSession.isValid) {
                                // Authenticated Status Bar
                                Surface(
                                    color = ObsidianGreen.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianGreen.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ObsidianGreen, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "OAuth: ${chatGPTSession.email.ifBlank { "Signed In" }}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ObsidianGreen
                                            )
                                        }
                                        TextButton(
                                            onClick = onSignOutOfChatGPT,
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Sign Out", fontSize = 11.sp, color = ObsidianRed)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "OpenAI Dynamic Models (${chatGPTModels.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextSecondary
                                    )
                                    OutlinedButton(
                                        onClick = onRefreshChatGPTModels,
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianGreen),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        if (isLoadingChatGPTModels) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = ObsidianGreen,
                                                strokeWidth = 1.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Fetching...", fontSize = 10.sp, color = ObsidianGreen)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = ObsidianGreen, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Sync OpenAI", fontSize = 10.sp, color = ObsidianGreen)
                                        }
                                    }
                                }

                                if (isLoadingChatGPTModels && chatGPTModels.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(color = ObsidianGreen, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Fetching live models from OpenAI endpoints...", fontSize = 11.sp, color = ObsidianTextSecondary)
                                        }
                                    }
                                } else if (chatGPTModels.isEmpty()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("No models fetched yet", fontSize = 12.sp, color = ObsidianTextSecondary)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Button(
                                                onClick = onRefreshChatGPTModels,
                                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianGreen),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("Fetch Live Models from OpenAI", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        chatGPTModels.forEach { model ->
                                            val isSelected = activeProvider == "chatgpt" && selectedChatGPTModel == model.id
                                            Surface(
                                                onClick = {
                                                    onSelectProvider("chatgpt")
                                                    onSelectChatGPTModel(model.id)
                                                    showModelDialog = false
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (isSelected) ObsidianGreen.copy(alpha = 0.15f) else ObsidianSurfaceElevated,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) ObsidianGreen else ObsidianBorder
                                                ),
                                                modifier = Modifier.fillMaxWidth().testTag("select_chatgpt_${model.id}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            onSelectProvider("chatgpt")
                                                            onSelectChatGPTModel(model.id)
                                                            showModelDialog = false
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = model.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                color = ObsidianTextPrimary
                                                            )
                                                            if (model.id.startsWith("o") || model.id.contains("4o")) {
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Surface(
                                                                    color = ObsidianGreen.copy(alpha = 0.2f),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                ) {
                                                                    Text(
                                                                        text = model.id,
                                                                        fontSize = 9.sp,
                                                                        fontFamily = FontFamily.Monospace,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = ObsidianGreen,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        if (model.description.isNotBlank()) {
                                                            Text(
                                                                text = model.description,
                                                                fontSize = 10.sp,
                                                                color = ObsidianTextSecondary
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Custom Model Input Accordion
                                Surface(
                                    onClick = { showAddCustomModel = !showAddCustomModel },
                                    color = ObsidianSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+ Enter Any Custom Model Slug",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = ObsidianTeal
                                        )
                                        Icon(
                                            if (showAddCustomModel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = ObsidianTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (showAddCustomModel) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ObsidianSurface, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customModelInput,
                                            onValueChange = { customModelInput = it },
                                            placeholder = { Text("e.g. gpt-4.5-preview, chatgpt-4o-latest, o3-mini", fontSize = 11.sp) },
                                            singleLine = true,
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = ObsidianTextPrimary),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        )
                                        Button(
                                            onClick = {
                                                if (customModelInput.isNotBlank()) {
                                                    onAddCustomChatGPTModel(customModelInput.trim(), customModelInput.trim())
                                                    showModelDialog = false
                                                    customModelInput = ""
                                                }
                                            },
                                            enabled = customModelInput.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianGreen),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                        ) {
                                            Text("Add & Select Model", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Not signed in with ChatGPT OAuth
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockPerson,
                                            contentDescription = null,
                                            tint = ObsidianGreen,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            text = "Connect ChatGPT via OAuth 2.0 PKCE",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ObsidianTextPrimary
                                        )
                                        Text(
                                            text = "Sign in to use your ChatGPT subscription directly (GPT-4o, o1, o3-mini) with native streaming, RAG vault search, and automated tool execution.",
                                            fontSize = 12.sp,
                                            color = ObsidianTextSecondary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                        Button(
                                            onClick = {
                                                showModelDialog = false
                                                onInitiateChatGPTLogin()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianGreen),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("chatgpt_signin_button")
                                        ) {
                                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Sign in with ChatGPT (PKCE)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
