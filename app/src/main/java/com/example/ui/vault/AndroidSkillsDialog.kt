package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import com.example.data.skills.*
import com.example.ui.theme.*
import org.json.JSONObject

@Composable
fun AndroidSkillsDialog(
    skillsCatalog: List<AndroidSkillDefinition>,
    telemetry: DeviceTelemetry?,
    displayMetrics: DisplayMetricsInfo?,
    runtimeJvm: RuntimeJvmInfo?,
    sensorsInfo: HardwareSensorsInfo?,
    storageAudit: StorageAudit?,
    knowledgeBase: List<AndroidKnowledgeTopic>,
    onRefreshTelemetry: () -> Unit,
    onExecuteDynamicScript: (String, JSONObject) -> Unit,
    onTestToast: (String) -> Unit,
    onTestHaptic: () -> Unit,
    onShareContent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Telemetry, 1: Skills, 2: Dynamic Scripts, 3: Full Knowledge

    LaunchedEffect(Unit) {
        onRefreshTelemetry()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("android_skills_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianPurpleContainer)
                                .border(1.dp, ObsidianPurple, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = ObsidianGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Android Skills & Knowledge",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianTextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ObsidianPurpleContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Dynamic Engine",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianPurpleLight,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Full OS telemetry, dynamic scripts & architecture",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ObsidianTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight,
                    edgePadding = 0.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Telemetry", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Skills (${skillsCatalog.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Dynamic Scripts", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Full Knowledge (${knowledgeBase.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> TelemetryTabContent(telemetry, displayMetrics, runtimeJvm, sensorsInfo, storageAudit, onRefreshTelemetry)
                    1 -> SkillsCatalogTabContent(skillsCatalog)
                    2 -> DynamicScriptsTabContent(onExecuteDynamicScript, onTestToast, onTestHaptic, onShareContent)
                    3 -> KnowledgeBaseTabContent(knowledgeBase)
                }
            }
        }
    }
}

@Composable
private fun TelemetryTabContent(
    telemetry: DeviceTelemetry?,
    displayMetrics: DisplayMetricsInfo?,
    runtimeJvm: RuntimeJvmInfo?,
    sensorsInfo: HardwareSensorsInfo?,
    storageAudit: StorageAudit?,
    onRefreshTelemetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEVICE TELEMETRY & HARDWARE HOOKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.1.sp,
                        color = ObsidianTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                TextButton(
                    onClick = onRefreshTelemetry,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ObsidianTeal, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Refresh", fontSize = 11.sp, color = ObsidianTeal)
                }
            }
        }

        if (telemetry != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("OS Platform", "Android ${telemetry.osVersion} (API ${telemetry.sdkInt})", Icons.Default.Info)
                        TelemetryRow("Model & Hardware", "${telemetry.manufacturer} ${telemetry.deviceModel}", Icons.Default.PhoneAndroid)
                        TelemetryRow("CPU ABI", telemetry.cpuAbi, Icons.Default.Memory)
                        TelemetryRow(
                            "RAM Memory",
                            "${telemetry.ramUsagePercent}% (${telemetry.availableRamMb} MB / ${telemetry.totalRamMb} MB free)",
                            Icons.Default.Speed
                        )
                        TelemetryRow(
                            "Battery Status",
                            "${telemetry.batteryPercent}% (${if (telemetry.isCharging) "Charging ⚡" else "Discharging"})",
                            Icons.Default.BatteryChargingFull
                        )
                        TelemetryRow("Network", "${telemetry.networkType} · ${if (telemetry.isNetworkConnected) "Online" else "Offline"}", Icons.Default.Wifi)
                        TelemetryRow("System Uptime", "${telemetry.uptimeMinutes} minutes", Icons.Default.Schedule)
                    }
                }
            }
        }

        if (displayMetrics != null) {
            item {
                Text(
                    text = "DISPLAY & WINDOW METRICS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("Screen Resolution", "${displayMetrics.widthPx} x ${displayMetrics.heightPx} px", Icons.Default.AspectRatio)
                        TelemetryRow("Screen Density", "${displayMetrics.densityDpi} DPI (${displayMetrics.densityScale}x)", Icons.Default.ScreenRotation)
                        TelemetryRow("Window Orientation", displayMetrics.orientation, Icons.Default.ScreenLockPortrait)
                    }
                }
            }
        }

        if (runtimeJvm != null) {
            item {
                Text(
                    text = "JVM RUNTIME & THREAD METRICS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("JVM Heap Total", "${runtimeJvm.jvmHeapTotalMb} MB (Max ${runtimeJvm.jvmHeapMaxMb} MB)", Icons.Default.Memory)
                        TelemetryRow("JVM Free Memory", "${runtimeJvm.jvmHeapFreeMb} MB", Icons.Default.Storage)
                        TelemetryRow("Active Thread Count", "${runtimeJvm.activeThreadCount} threads", Icons.Default.Grain)
                        TelemetryRow("CPU Core Count", "${runtimeJvm.availableProcessors} cores", Icons.Default.DeveloperBoard)
                    }
                }
            }
        }

        if (sensorsInfo != null) {
            item {
                Text(
                    text = "HARDWARE SENSORS DETECTED",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("Total Sensors Found", "${sensorsInfo.totalSensorsFound} hardware sensors", Icons.Default.Sensors)
                        TelemetryRow("Accelerometer", if (sensorsInfo.hasAccelerometer) "Present ✅" else "Not detected", Icons.Default.Explore)
                        TelemetryRow("Gyroscope", if (sensorsInfo.hasGyroscope) "Present ✅" else "Not detected", Icons.Default.ScreenRotation)
                        TelemetryRow("Ambient Light Sensor", if (sensorsInfo.hasLightSensor) "Present ✅" else "Not detected", Icons.Default.LightMode)
                    }
                }
            }
        }

        if (storageAudit != null) {
            item {
                Text(
                    text = "PERSISTENT DISK STORAGE PARTITION",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("Vault Path", storageAudit.vaultPath, Icons.Default.Folder)
                        TelemetryRow("Indexed Files", "${storageAudit.totalFiles} files (${storageAudit.totalSizeBytes / 1024} KB)", Icons.Default.Description)
                        TelemetryRow("Free Partition Space", "${storageAudit.freeSpaceBytes / (1024 * 1024 * 1024)} GB / ${storageAudit.totalPartitionBytes / (1024 * 1024 * 1024)} GB", Icons.Default.Storage)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = ObsidianPurpleLight, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 12.sp, color = ObsidianTextSecondary)
        }
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ObsidianTextPrimary,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SkillsCatalogTabContent(skills: List<AndroidSkillDefinition>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(skills, key = { it.id }) { skill ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = skill.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ObsidianTextPrimary
                        )
                        Surface(
                            color = ObsidianPurpleContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = skill.category,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPurpleLight,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = skill.description,
                        fontSize = 11.sp,
                        color = ObsidianTextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = skill.sampleUsage,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ObsidianTeal,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicScriptsTabContent(
    onExecuteDynamicScript: (String, JSONObject) -> Unit,
    onTestToast: (String) -> Unit,
    onTestHaptic: () -> Unit,
    onShareContent: (String) -> Unit
) {
    var regexPattern by remember { mutableStateOf("#([a-zA-Z0-9]+)") }
    var regexReplacement by remember { mutableStateOf("#$1") }
    var showRegexDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "DYNAMIC VAULT AUTOMATION SCRIPTS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.1.sp,
                    color = ObsidianTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        item {
            ScriptCard(
                title = "📋 Task Aggregator MOC",
                description = "Scans all vault notes for pending/completed markdown checkboxes and builds [[Master Tasks MOC]].",
                buttonLabel = "Run Aggregator",
                onClick = { onExecuteDynamicScript("todo_aggregator", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "📑 Frontmatter Injector",
                description = "Dynamically injects structured YAML frontmatter (title, date, wordCount) into notes.",
                buttonLabel = "Inject YAML",
                onClick = { onExecuteDynamicScript("frontmatter_injector", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "📊 Word Frequency & Lexical Analytics",
                description = "Computes lexical diversity, token occurrences, and conceptual frequency into [[Lexical Analytics]].",
                buttonLabel = "Analyze Lexicon",
                onClick = { onExecuteDynamicScript("word_frequency_analyzer", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "📤 Export Vault JSON Manifest",
                description = "Serializes the entire vault graph, outlinks, and timestamps to vault_export.json.",
                buttonLabel = "Export JSON",
                onClick = { onExecuteDynamicScript("export_vault_json", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "🔗 Wikilink Normalizer",
                description = "Cleans whitespace and standardizes [[wikilink]] syntax across all Markdown documents.",
                buttonLabel = "Normalize Links",
                onClick = { onExecuteDynamicScript("wikilink_normalizer", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "📦 Backup Vault (.zip snapshot)",
                description = "Creates a compressed zip archive of all notes in Download/ObsidianVault_Backups/.",
                buttonLabel = "Run Backup",
                onClick = { onExecuteDynamicScript("backup_vault", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "🗺️ Generate MOC Index",
                description = "Traverses the directory hierarchy and writes an automated [[MOC Index]] map of content.",
                buttonLabel = "Generate MOC",
                onClick = { onExecuteDynamicScript("generate_moc_index", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "🧹 Purge Empty Stub Files",
                description = "Cleans up zero-byte files and empty placeholder notes across the vault.",
                buttonLabel = "Clean Stubs",
                onClick = { onExecuteDynamicScript("clean_empty_files", JSONObject()) }
            )
        }

        item {
            ScriptCard(
                title = "🔔 Test Android Toast & Haptic Hook",
                description = "Dispatches a native Android screen Toast notification and triggers tactile vibration.",
                buttonLabel = "Test Dispatch",
                onClick = {
                    onTestHaptic()
                    onTestToast("Venice AI native Android hook executed successfully.")
                }
            )
        }

        item {
            ScriptCard(
                title = "📤 Test System Intent Share",
                description = "Opens the native Android system share dialog.",
                buttonLabel = "Test Share",
                onClick = {
                    onShareContent("Shared via Venice AI Obsidian Vault native Android skill.")
                }
            )
        }
    }
}

@Composable
private fun KnowledgeBaseTabContent(knowledgeBase: List<AndroidKnowledgeTopic>) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, knowledgeBase) {
        if (searchQuery.isBlank()) knowledgeBase
        else knowledgeBase.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.summary.contains(searchQuery, ignoreCase = true) ||
            it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Android architecture knowledge...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ObsidianTextSecondary) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ObsidianSurfaceElevated,
                unfocusedContainerColor = ObsidianSurfaceElevated,
                focusedBorderColor = ObsidianPurple,
                unfocusedBorderColor = ObsidianBorder
            ),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered, key = { it.id }) { topic ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = topic.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ObsidianTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = ObsidianPurpleContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = topic.category,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianPurpleLight,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = topic.summary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = ObsidianTeal
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = topic.details,
                            fontSize = 11.sp,
                            color = ObsidianTextSecondary,
                            lineHeight = 16.sp
                        )

                        if (topic.codeSnippet.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = ObsidianBackground,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = topic.codeSnippet,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ObsidianYellow,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    title: String,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, fontSize = 11.sp, color = ObsidianTextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = buttonLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
