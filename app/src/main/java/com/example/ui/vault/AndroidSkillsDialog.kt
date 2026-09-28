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
import com.example.data.scripts.DynamicScriptRule
import com.example.data.skills.*
import com.example.ui.theme.*
import org.json.JSONObject

@Composable
fun AndroidSkillsDialog(
    skillsCatalog: List<AndroidSkillDefinition>,
    dynamicScriptRules: List<DynamicScriptRule>,
    telemetry: DeviceTelemetry?,
    displayMetrics: DisplayMetricsInfo?,
    runtimeJvm: RuntimeJvmInfo?,
    sensorsInfo: HardwareSensorsInfo?,
    storageAudit: StorageAudit?,
    knowledgeBase: List<AndroidKnowledgeTopic>,
    onRefreshTelemetry: () -> Unit,
    onExecuteDynamicScript: (String, JSONObject) -> Unit,
    onExecuteCustomRule: (DynamicScriptRule) -> Unit,
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
                                    text = "Android Skills & Inotify Hub",
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
                                        text = "Inotify Active ⚡",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ObsidianGreen,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Live Kernel FileObserver · Telemetry · Dynamic Scripts",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_android_skills_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Telemetry", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("OS Skills", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Dynamic Scripts", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Architecture", fontSize = 11.sp) },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> TelemetryTabContent(
                            telemetry = telemetry,
                            displayMetrics = displayMetrics,
                            runtimeJvm = runtimeJvm,
                            sensorsInfo = sensorsInfo,
                            storageAudit = storageAudit,
                            onRefresh = onRefreshTelemetry
                        )
                        1 -> SkillsCatalogTabContent(skillsCatalog)
                        2 -> DynamicScriptsTabContent(
                            dynamicScriptRules = dynamicScriptRules,
                            onExecuteDynamicScript = onExecuteDynamicScript,
                            onExecuteCustomRule = onExecuteCustomRule,
                            onTestToast = onTestToast,
                            onTestHaptic = onTestHaptic,
                            onShareContent = onShareContent
                        )
                        3 -> KnowledgeBaseTabContent(knowledgeBase)
                    }
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
    onRefresh: () -> Unit
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
                    text = "REALTIME KERNEL & HARDWARE TELEMETRY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.1.sp,
                        color = ObsidianTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ObsidianPurpleLight, modifier = Modifier.size(16.dp))
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
                        TelemetryRow("Android Version", "Android ${telemetry.osVersion} (API Level ${telemetry.sdkInt})", Icons.Default.Info)
                        TelemetryRow("Hardware Device", "${telemetry.manufacturer} ${telemetry.deviceModel}", Icons.Default.PhoneAndroid)
                        TelemetryRow("CPU Architecture", telemetry.cpuAbi, Icons.Default.Memory)
                        TelemetryRow("Battery Level", "${telemetry.batteryPercent}% (${if (telemetry.isCharging) "Charging ⚡" else "Discharging"})", Icons.Default.BatteryChargingFull)
                        TelemetryRow("RAM Available / Total", "${telemetry.availableRamMb} MB free / ${telemetry.totalRamMb} MB (${telemetry.ramUsagePercent}% used)", Icons.Default.Storage)
                        TelemetryRow("System Uptime", "${telemetry.uptimeMinutes} minutes", Icons.Default.Timer)
                        TelemetryRow("Network Connection", "${telemetry.networkType} (${if (telemetry.isNetworkConnected) "Connected ✅" else "Offline ❌"})", Icons.Default.Wifi)
                    }
                }
            }
        }

        if (displayMetrics != null) {
            item {
                Text(
                    text = "DISPLAY & SCREEN METRICS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("Screen Resolution", "${displayMetrics.widthPx} x ${displayMetrics.heightPx} px", Icons.Default.AspectRatio)
                        TelemetryRow("Density DPI", "${displayMetrics.densityDpi} dpi (Scale ${displayMetrics.densityScale}x)", Icons.Default.ZoomIn)
                        TelemetryRow("Orientation", displayMetrics.orientation, Icons.Default.ScreenRotation)
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
                        TelemetryRow("Vault Path (Read/Write)", storageAudit.vaultPath, Icons.Default.Folder)
                        TelemetryRow("Room Database Path", storageAudit.roomDatabasePath, Icons.Default.Storage)
                        TelemetryRow("Indexed Notes", "${storageAudit.totalFiles} files (${storageAudit.totalSizeBytes / 1024} KB)", Icons.Default.Description)
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
    dynamicScriptRules: List<DynamicScriptRule>,
    onExecuteDynamicScript: (String, JSONObject) -> Unit,
    onExecuteCustomRule: (DynamicScriptRule) -> Unit,
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
        // Active inotify live scripts from .scripts/
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "RUNTIME SCRIPTS & PLUGINS (.scripts/)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.1.sp,
                        color = ObsidianTeal,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = ObsidianPurpleContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${dynamicScriptRules.size} rules loaded",
                        fontSize = 9.sp,
                        color = ObsidianPurpleLight,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        items(dynamicScriptRules, key = { it.filePath }) { rule ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rule.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ObsidianTextPrimary
                        )
                        Surface(
                            color = ObsidianBackground,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Trigger: ${rule.trigger}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ObsidianYellow,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = rule.description, fontSize = 11.sp, color = ObsidianTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { onExecuteCustomRule(rule) },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Script", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Divider(color = ObsidianBorder)
            Text(
                text = "BUILT-IN AUTOMATION HOOKS",
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
                title = "🔤 Batch Regex Pattern Replacer",
                description = "Applies custom regex string substitution across all Markdown files in the vault.",
                buttonLabel = "Configure & Run",
                onClick = { showRegexDialog = true }
            )
        }

        item {
            Text(
                text = "INTERACTIVE OS HOOK TESTING",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp, color = ObsidianTextSecondary, fontWeight = FontWeight.Bold)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onTestToast("Venice AI Android Hook Active") },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger Toast", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onTestHaptic,
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tactile Haptic", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { onShareContent("Obsidian Vault: Knowledge Architecture on Android\nPersisted to external storage with Inotify Hot-Reloading.") },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dispatch Native Android Share Sheet", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showRegexDialog) {
        AlertDialog(
            onDismissRequest = { showRegexDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val params = JSONObject().apply {
                            put("pattern", regexPattern)
                            put("replacement", regexReplacement)
                        }
                        onExecuteDynamicScript("regex_replace", params)
                        showRegexDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple)
                ) {
                    Text("Execute Replace")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegexDialog = false }) {
                    Text("Cancel", color = ObsidianTextSecondary)
                }
            },
            title = { Text("Batch Regex Replacement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter regular expression pattern and replacement string to apply across all vault notes:", fontSize = 12.sp, color = ObsidianTextSecondary)
                    OutlinedTextField(
                        value = regexPattern,
                        onValueChange = { regexPattern = it },
                        label = { Text("Regex Pattern") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regexReplacement,
                        onValueChange = { regexReplacement = it },
                        label = { Text("Replacement String") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = ObsidianSurfaceElevated,
            titleContentColor = ObsidianTextPrimary
        )
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
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ObsidianTextPrimary)
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = description, fontSize = 11.sp, color = ObsidianTextSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(buttonLabel, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeBaseTabContent(knowledge: List<AndroidKnowledgeTopic>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(knowledge, key = { it.id }) { topic ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = topic.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ObsidianTextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = topic.summary, fontSize = 11.sp, color = ObsidianTextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = ObsidianBackground,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = topic.codeSnippet,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ObsidianPurpleLight,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}
