package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.data.skills.AndroidSkillDefinition
import com.example.data.skills.DeviceTelemetry
import com.example.data.skills.StorageAudit
import com.example.ui.theme.*

@Composable
fun AndroidSkillsDialog(
    skillsCatalog: List<AndroidSkillDefinition>,
    telemetry: DeviceTelemetry?,
    storageAudit: StorageAudit?,
    onRefreshTelemetry: () -> Unit,
    onExecuteHook: (String) -> Unit,
    onTestToast: (String) -> Unit,
    onTestHaptic: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Telemetry & Storage, 1: Installed Skills, 2: Script Hooks

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
                .fillMaxHeight(0.85f)
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
                            Text(
                                text = "Native Android Skills",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                            )
                            Text(
                                text = "System hooks, telemetry & script engine",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight
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
                        text = { Text("Run Scripts", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> TelemetryTabContent(telemetry, storageAudit, onRefreshTelemetry)
                    1 -> SkillsCatalogTabContent(skillsCatalog)
                    2 -> ScriptHooksTabContent(onExecuteHook, onTestToast, onTestHaptic)
                }
            }
        }
    }
}

@Composable
private fun TelemetryTabContent(
    telemetry: DeviceTelemetry?,
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
                    text = "HARDWARE & SYSTEM HOOKS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
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
                        TelemetryRow("OS Version", "Android ${telemetry.osVersion} (API ${telemetry.sdkInt})", Icons.Default.Info)
                        TelemetryRow("Device Model", "${telemetry.manufacturer} ${telemetry.deviceModel}", Icons.Default.PhoneAndroid)
                        TelemetryRow("CPU Architecture", telemetry.cpuAbi, Icons.Default.Memory)
                        TelemetryRow(
                            "RAM Usage",
                            "${telemetry.ramUsagePercent}% (${telemetry.availableRamMb} MB / ${telemetry.totalRamMb} MB free)",
                            Icons.Default.Speed
                        )
                        TelemetryRow(
                            "Battery",
                            "${telemetry.batteryPercent}% (${if (telemetry.isCharging) "Charging ⚡" else "Discharging"})",
                            Icons.Default.BatteryChargingFull
                        )
                        TelemetryRow("Network Connection", "${telemetry.networkType} · ${if (telemetry.isNetworkConnected) "Online" else "Offline"}", Icons.Default.Wifi)
                        TelemetryRow("System Uptime", "${telemetry.uptimeMinutes} minutes", Icons.Default.Schedule)
                    }
                }
            }
        }

        item {
            Text(
                text = "PHYSICAL STORAGE PARTITION",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    color = ObsidianTextSecondary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (storageAudit != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryRow("Vault Location", storageAudit.vaultPath, Icons.Default.Folder)
                        TelemetryRow("Indexed Vault Files", "${storageAudit.totalFiles} files (${storageAudit.totalSizeBytes / 1024} KB)", Icons.Default.Description)
                        TelemetryRow("Free Partition Space", "${storageAudit.freeSpaceBytes / (1024 * 1024 * 1024)} GB free / ${storageAudit.totalPartitionBytes / (1024 * 1024 * 1024)} GB total", Icons.Default.Storage)
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
private fun ScriptHooksTabContent(
    onExecuteHook: (String) -> Unit,
    onTestToast: (String) -> Unit,
    onTestHaptic: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "AUTOMATION & SYSTEM SCRIPTS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    color = ObsidianTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        item {
            ScriptCard(
                title = "📦 Backup Vault (.zip snapshot)",
                description = "Creates a compressed zip archive of all Markdown notes in Download/ObsidianVault_Backups/",
                buttonLabel = "Run Backup",
                onClick = { onExecuteHook("backup_vault") }
            )
        }

        item {
            ScriptCard(
                title = "🗺️ Generate MOC Index (Map of Content)",
                description = "Traverses the vault directory hierarchy and writes an automated [[MOC Index]] note.",
                buttonLabel = "Generate MOC",
                onClick = { onExecuteHook("generate_moc_index") }
            )
        }

        item {
            ScriptCard(
                title = "🧹 Purge Empty Stub Files",
                description = "Cleans up zero-byte notes and empty placeholder files across the filesystem.",
                buttonLabel = "Clean Stubs",
                onClick = { onExecuteHook("clean_empty_files") }
            )
        }

        item {
            ScriptCard(
                title = "🔔 Test Android Toast & Haptic Hook",
                description = "Dispatches a native Android screen Toast notification and triggers device tactile vibration.",
                buttonLabel = "Test Dispatch",
                onClick = {
                    onTestHaptic()
                    onTestToast("Venice AI native Android hook executed successfully.")
                }
            )
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
