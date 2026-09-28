package com.example.data.skills

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AndroidSkillDefinition(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val sampleUsage: String
)

data class DeviceTelemetry(
    val osVersion: String,
    val sdkInt: Int,
    val deviceModel: String,
    val manufacturer: String,
    val cpuAbi: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val availableRamMb: Long,
    val totalRamMb: Long,
    val ramUsagePercent: Int,
    val uptimeMinutes: Long,
    val networkType: String,
    val isNetworkConnected: Boolean
)

data class StorageAudit(
    val vaultPath: String,
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val freeSpaceBytes: Long,
    val totalPartitionBytes: Long,
    val folderBreakdown: Map<String, Int>
)

class AndroidSkillsManager(private val context: Context) {

    val skillsCatalog: List<AndroidSkillDefinition> = listOf(
        AndroidSkillDefinition(
            id = "get_device_telemetry",
            name = "Device Telemetry & Hardware Hook",
            category = "System & Hardware",
            description = "Inspects Android OS version, SDK level, CPU ABI, RAM usage, battery state, and uptime.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"get_device_telemetry\"}"
        ),
        AndroidSkillDefinition(
            id = "get_storage_audit",
            name = "Storage & Partitions Inspector",
            category = "Filesystem & Storage",
            description = "Audits physical disk space, external storage partitions, and directory trees.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"get_storage_audit\"}"
        ),
        AndroidSkillDefinition(
            id = "clipboard_write",
            name = "Clipboard Manager Hook",
            category = "System Bridge",
            description = "Pushes text to the native Android system clipboard.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"clipboard_write\", \"text\": \"Content to copy\"}"
        ),
        AndroidSkillDefinition(
            id = "clipboard_read",
            name = "Clipboard Reader Hook",
            category = "System Bridge",
            description = "Reads current text payload from Android clipboard.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"clipboard_read\"}"
        ),
        AndroidSkillDefinition(
            id = "trigger_toast",
            name = "System Toast Dispatcher",
            category = "User Interface & Alerts",
            description = "Fires a native Android Toast notification on the user's screen.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"trigger_toast\", \"message\": \"Sync complete!\"}"
        ),
        AndroidSkillDefinition(
            id = "trigger_haptic",
            name = "Haptic Feedback Dispatcher",
            category = "Hardware & Sensors",
            description = "Triggers device tactile vibration feedback.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"trigger_haptic\", \"duration_ms\": 50}"
        ),
        AndroidSkillDefinition(
            id = "execute_hook",
            name = "Vault Automation Hooks & Scripts",
            category = "Automation & Scripts",
            description = "Executes native maintenance scripts (backup_vault, generate_moc_index, clean_empty_files).",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"execute_hook\", \"hook_name\": \"generate_moc_index\"}"
        )
    )

    fun getDeviceTelemetry(): DeviceTelemetry {
        // Battery status
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val ramUsagePct = if (totalMb > 0) (((totalMb - availMb) * 100) / totalMb).toInt() else 0

        // Network
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)
        val isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected / Offline"
        }

        val uptimeMin = SystemClock.elapsedRealtime() / (1000 * 60)

        return DeviceTelemetry(
            osVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL ?: "Generic Android",
            manufacturer = Build.MANUFACTURER ?: "Android",
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            batteryPercent = batteryPct,
            isCharging = isCharging,
            availableRamMb = availMb,
            totalRamMb = totalMb,
            ramUsagePercent = ramUsagePct,
            uptimeMinutes = uptimeMin,
            networkType = netType,
            isNetworkConnected = isConnected
        )
    }

    suspend fun getStorageAudit(vaultDir: File): StorageAudit = withContext(Dispatchers.IO) {
        val files = vaultDir.walkTopDown().filter { it.isFile }.toList()
        val totalBytes = files.sumOf { it.length() }
        val freeBytes = vaultDir.freeSpace
        val totalPartBytes = vaultDir.totalSpace

        val folderBreakdown = mutableMapOf<String, Int>()
        files.forEach { file ->
            val folder = if (file.parentFile != null && file.parentFile != vaultDir) file.parentFile!!.name else "Root"
            folderBreakdown[folder] = (folderBreakdown[folder] ?: 0) + 1
        }

        StorageAudit(
            vaultPath = vaultDir.absolutePath,
            totalFiles = files.size,
            totalSizeBytes = totalBytes,
            freeSpaceBytes = freeBytes,
            totalPartitionBytes = totalPartBytes,
            folderBreakdown = folderBreakdown
        )
    }

    fun copyToClipboard(label: String, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readClipboard(): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun showToast(message: String) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun triggerHaptic(durationMs: Long = 50) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    // Automation Hooks & Scripts
    suspend fun executeHook(hookName: String, vaultDir: File, params: JSONObject = JSONObject()): String = withContext(Dispatchers.IO) {
        when (hookName.lowercase()) {
            "backup_vault" -> {
                val backupDir = File(vaultDir.parentFile ?: vaultDir, "ObsidianVault_Backups").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val zipFile = File(backupDir, "Vault_Backup_$timeStamp.zip")

                val mdFiles = vaultDir.walkTopDown().filter { it.isFile && (it.extension == "md" || it.extension == "txt") }.toList()
                if (mdFiles.isEmpty()) {
                    return@withContext "⚠️ Backup aborted: Vault directory contains no files to archive."
                }

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    for (file in mdFiles) {
                        val relPath = file.relativeTo(vaultDir).path
                        val entry = ZipEntry(relPath)
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
                "✅ **Vault Backup Complete**: Created archive `${zipFile.name}` (${mdFiles.size} files, ${zipFile.length() / 1024} KB) at `${zipFile.absolutePath}`."
            }

            "generate_moc_index" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                val grouped = files.groupBy {
                    if (it.parentFile != null && it.parentFile != vaultDir) it.parentFile!!.name else "General"
                }

                val sb = StringBuilder()
                sb.appendLine("# 🗺️ Map of Content (Vault MOC Index)")
                sb.appendLine()
                sb.appendLine("> *Generated automatically by Venice AI Android Automation Hook on ${Date()}*")
                sb.appendLine()
                sb.appendLine("## Vault Statistics")
                sb.appendLine("- **Total Documents**: ${files.size}")
                sb.appendLine("- **Folders**: ${grouped.keys.size}")
                sb.appendLine()

                for ((folder, noteFiles) in grouped.toSortedMap()) {
                    sb.appendLine("### 📁 $folder")
                    for (f in noteFiles.sortedBy { it.nameWithoutExtension }) {
                        sb.appendLine("- [[${f.nameWithoutExtension}]]")
                    }
                    sb.appendLine()
                }

                val mocFile = File(vaultDir, "MOC Index.md")
                mocFile.writeText(sb.toString())
                "✅ **MOC Generated**: Created/updated `[[MOC Index]]` with ${files.size} indexed documents across ${grouped.keys.size} folders."
            }

            "clean_empty_files" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                var removed = 0
                for (f in files) {
                    val content = try { f.readText().trim() } catch (e: Exception) { "" }
                    if (content.isEmpty() || content == "# ${f.nameWithoutExtension}") {
                        f.delete()
                        removed++
                    }
                }
                "✅ **Cleanup Hook Finished**: Inspected ${files.size} files; purged $removed empty stub files."
            }

            else -> {
                "⚠️ Unknown automation hook: '$hookName'. Supported hooks: `backup_vault`, `generate_moc_index`, `clean_empty_files`."
            }
        }
    }
}
