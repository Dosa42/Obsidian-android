package com.example.data.skills

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import java.util.regex.Pattern
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

data class DisplayMetricsInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val orientation: String
)

data class RuntimeJvmInfo(
    val jvmHeapFreeMb: Long,
    val jvmHeapTotalMb: Long,
    val jvmHeapMaxMb: Long,
    val activeThreadCount: Int,
    val availableProcessors: Int
)

data class HardwareSensorsInfo(
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val hasLightSensor: Boolean,
    val hasProximitySensor: Boolean,
    val totalSensorsFound: Int
)

data class StorageAudit(
    val vaultPath: String,
    val roomDatabasePath: String,
    val internalFilesDir: String,
    val totalFiles: Int,
    val totalSizeBytes: Long,
    val freeSpaceBytes: Long,
    val totalPartitionBytes: Long,
    val folderBreakdown: Map<String, Int>
)

data class AndroidKnowledgeTopic(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val details: String,
    val codeSnippet: String
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
            id = "get_display_metrics",
            name = "Display & Screen Metrics Hook",
            category = "Display & UI",
            description = "Queries screen resolution in pixels, DPI density, scale factor, and orientation.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"get_display_metrics\"}"
        ),
        AndroidSkillDefinition(
            id = "get_runtime_jvm",
            name = "JVM Runtime & Thread Hook",
            category = "Runtime & Memory",
            description = "Inspects JVM Heap allocation (free/total/max), active threads, and core count.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"get_runtime_jvm\"}"
        ),
        AndroidSkillDefinition(
            id = "get_hardware_sensors",
            name = "Sensor Hardware Telemetry",
            category = "Sensors & Hardware",
            description = "Detects hardware sensors: Accelerometer, Gyroscope, Ambient Light, and Proximity.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"get_hardware_sensors\"}"
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
            id = "share_content",
            name = "System Intent Share Hook",
            category = "Intents & IPC",
            description = "Triggers native Android ACTION_SEND share sheet for note text or summary.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"share_content\", \"text\": \"Note content\", \"title\": \"Share Note\"}"
        ),
        AndroidSkillDefinition(
            id = "execute_dynamic_script",
            name = "Dynamic Vault Script Engine",
            category = "Dynamic Scripts",
            description = "Executes custom or parameterized scripts: frontmatter_injector, todo_aggregator, wikilink_normalizer, word_frequency_analyzer, export_vault_json, regex_replace.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"execute_dynamic_script\", \"script\": \"todo_aggregator\"}"
        ),
        AndroidSkillDefinition(
            id = "query_android_knowledge",
            name = "Android Full Knowledge Engine",
            category = "Knowledge Base",
            description = "Provides in-depth architectural knowledge for Jetpack Compose, Scoped Storage, Coroutines, Room, and Android OS.",
            sampleUsage = "{\"action\": \"android_skill\", \"skill\": \"query_android_knowledge\", \"query\": \"scoped_storage\"}"
        )
    )

    val androidKnowledgeBase: List<AndroidKnowledgeTopic> = listOf(
        AndroidKnowledgeTopic(
            id = "scoped_storage",
            title = "Android Scoped Storage & Filesystem Architecture",
            category = "Storage Architecture",
            summary = "Understanding file access rules from Android 10 (API 29) to Android 15 (API 35).",
            details = """
                Android isolates app storage into App-Specific (Context.filesDir) and Shared Storage (Downloads, Documents, MediaStore).
                - Use requestLegacyExternalStorage="true" for API 29 backwards compatibility.
                - Public directories like Environment.DIRECTORY_DOWNLOADS persist across uninstall/reinstall.
                - Scoped Storage prevents arbitrary disk access while providing clean public media and document compartments.
            """.trimIndent(),
            codeSnippet = """
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val vault = File(downloadDir, "ObsidianVault").apply { if (!exists()) mkdirs() }
            """.trimIndent()
        ),
        AndroidKnowledgeTopic(
            id = "compose_state",
            title = "Jetpack Compose State & Recomposition Optimization",
            category = "UI Framework",
            summary = "Patterns for avoiding redundant recompositions and memory leaks in Compose.",
            details = """
                - MutableStateFlow collected with collectAsStateWithLifecycle() ensures lifecycle-aware collection.
                - Use remember { ... } to cache calculations across recompositions.
                - Use derivedStateOf { ... } when state changes frequently (e.g. scroll position) but only triggers actions on thresholds.
                - Never execute long-running I/O work in Composables; delegate to ViewModel and CoroutineScope.
            """.trimIndent(),
            codeSnippet = """
                val isScrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            """.trimIndent()
        ),
        AndroidKnowledgeTopic(
            id = "coroutines_flow",
            title = "Kotlin Coroutines & Flow Topology",
            category = "Concurrency",
            summary = "Structured concurrency, Dispatcher routing, and cold vs hot streams.",
            details = """
                - Dispatchers.IO is optimized for disk I/O, network calls, and SQLite queries (64+ threads).
                - Dispatchers.Default is dedicated to CPU-bound tasks (tokenization, graph calculation, JSON parsing).
                - StateFlow is a hot state-holder requiring an initial value, while SharedFlow is ideal for one-shot events.
            """.trimIndent(),
            codeSnippet = """
                viewModelScope.launch(Dispatchers.IO) {
                    val count = repository.syncVault()
                    withContext(Dispatchers.Main) { _uiStatus.value = "Synced" }
                }
            """.trimIndent()
        ),
        AndroidKnowledgeTopic(
            id = "room_sqlite",
            title = "Room Database Architecture & Performance",
            category = "Data Persistence",
            summary = "Object-relational mapping, SQLite indexing, and live Flow queries.",
            details = """
                - Room translates SQL into Kotlin type-safe functions at compile-time via KSP.
                - Mark queries with Flow<List<T>> to get live reactive notifications when underlying tables change.
                - Always index query-heavy columns (e.g. path, title, lastModified) to avoid full table scans.
                - Use Write-Ahead Logging (WAL) mode for concurrent readers while writes execute.
            """.trimIndent(),
            codeSnippet = """
                @Dao
                interface VaultDao {
                    @Query("SELECT * FROM vault_notes WHERE title LIKE '%' || :query || '%'")
                    fun searchNotes(query: String): Flow<List<VaultNote>>
                }
            """.trimIndent()
        ),
        AndroidKnowledgeTopic(
            id = "system_intents",
            title = "Android Intent Dispatcher & IPC",
            category = "Inter-Process Communication",
            summary = "Explicit and implicit intents, share sheets, and permissions.",
            details = """
                - Implicit intents allow delegating actions (Share, View URL, Open File) to the Android OS.
                - Flag Intent.FLAG_ACTIVITY_NEW_TASK is mandatory when starting activities from non-Activity contexts.
                - Intent.createChooser ensures the system shows standard Android Share Sheet.
            """.trimIndent(),
            codeSnippet = """
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, noteContent)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Note"))
            """.trimIndent()
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

    fun getDisplayMetrics(): DisplayMetricsInfo {
        val dm = context.resources.displayMetrics
        val isPortrait = dm.heightPixels >= dm.widthPixels
        return DisplayMetricsInfo(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            densityDpi = dm.densityDpi,
            densityScale = dm.density,
            orientation = if (isPortrait) "Portrait" else "Landscape"
        )
    }

    fun getRuntimeJvmInfo(): RuntimeJvmInfo {
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        return RuntimeJvmInfo(
            jvmHeapFreeMb = rt.freeMemory() / mb,
            jvmHeapTotalMb = rt.totalMemory() / mb,
            jvmHeapMaxMb = rt.maxMemory() / mb,
            activeThreadCount = Thread.activeCount(),
            availableProcessors = rt.availableProcessors()
        )
    }

    fun getHardwareSensorsInfo(): HardwareSensorsInfo {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val hasAccel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasLight = sm?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        val hasProx = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
        val allSensors = sm?.getSensorList(Sensor.TYPE_ALL)?.size ?: 0

        return HardwareSensorsInfo(
            hasAccelerometer = hasAccel,
            hasGyroscope = hasGyro,
            hasLightSensor = hasLight,
            hasProximitySensor = hasProx,
            totalSensorsFound = allSensors
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

        val dbFile = context.getDatabasePath("obsidian_vault.db")
        val internalDir = context.filesDir

        StorageAudit(
            vaultPath = vaultDir.absolutePath,
            roomDatabasePath = dbFile.absolutePath,
            internalFilesDir = internalDir.absolutePath,
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

    fun shareContent(text: String, title: String = "Share Note") {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (_: Exception) {}
    }

    // Dynamic Script Engine
    suspend fun executeDynamicScript(scriptId: String, vaultDir: File, params: JSONObject = JSONObject()): String = withContext(Dispatchers.IO) {
        when (scriptId.lowercase()) {
            "todo_aggregator" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                val pendingTasks = mutableListOf<Pair<String, String>>() // noteTitle -> task
                val completedTasks = mutableListOf<Pair<String, String>>()

                val todoPattern = Pattern.compile("^\\s*-\\s*\\[([ xX])\\]\\s*(.*)$")

                for (f in files) {
                    val lines = try { f.readLines() } catch (e: Exception) { emptyList() }
                    for (line in lines) {
                        val m = todoPattern.matcher(line)
                        if (m.find()) {
                            val status = m.group(1)
                            val taskText = m.group(2) ?: ""
                            if (status == " ") {
                                pendingTasks.add(f.nameWithoutExtension to taskText)
                            } else {
                                completedTasks.add(f.nameWithoutExtension to taskText)
                            }
                        }
                    }
                }

                val sb = StringBuilder()
                sb.appendLine("# 📋 Master Tasks MOC")
                sb.appendLine()
                sb.appendLine("> *Dynamically aggregated from all notes by Venice AI Android Script Engine on ${Date()}*")
                sb.appendLine()
                sb.appendLine("## ⏳ Pending Tasks (${pendingTasks.size})")
                if (pendingTasks.isEmpty()) {
                    sb.appendLine("- *No pending tasks found in vault.*")
                } else {
                    for ((note, task) in pendingTasks) {
                        sb.appendLine("- [ ] $task — *from [[$note]]*")
                    }
                }
                sb.appendLine()
                sb.appendLine("## ✅ Completed Tasks (${completedTasks.size})")
                for ((note, task) in completedTasks) {
                    sb.appendLine("- [x] $task — *from [[$note]]*")
                }

                val taskFile = File(vaultDir, "Master Tasks MOC.md")
                taskFile.writeText(sb.toString())
                "✅ **Task Aggregator Completed**: Found ${pendingTasks.size} pending and ${completedTasks.size} completed tasks. Written to `[[Master Tasks MOC]]`."
            }

            "frontmatter_injector" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                var updated = 0
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                for (f in files) {
                    if (f.name == "Master Tasks MOC.md" || f.name == "MOC Index.md") continue
                    val content = try { f.readText() } catch (e: Exception) { "" }
                    if (!content.startsWith("---")) {
                        val words = content.split("\\s+".toRegex()).count { it.isNotBlank() }
                        val frontmatter = buildString {
                            appendLine("---")
                            appendLine("title: \"${f.nameWithoutExtension}\"")
                            appendLine("updated: $dateStr")
                            appendLine("wordCount: $words")
                            appendLine("---")
                            appendLine()
                        }
                        f.writeText(frontmatter + content)
                        updated++
                    }
                }
                "✅ **Frontmatter Script Completed**: Injected structured YAML metadata into $updated notes."
            }

            "word_frequency_analyzer" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                val wordFreq = mutableMapOf<String, Int>()
                val stopWords = setOf("the", "and", "a", "to", "of", "in", "is", "that", "for", "it", "as", "was", "with", "be", "by", "on", "not", "this", "are", "from")

                var totalWords = 0
                for (f in files) {
                    val text = try { f.readText().lowercase() } catch (e: Exception) { "" }
                    val words = text.split("[^a-zA-Z0-9_-]+".toRegex()).filter { it.length > 2 && it !in stopWords }
                    totalWords += words.size
                    for (w in words) {
                        wordFreq[w] = (wordFreq[w] ?: 0) + 1
                    }
                }

                val topWords = wordFreq.entries.sortedByDescending { it.value }.take(15)

                val sb = StringBuilder()
                sb.appendLine("# 📊 Vault Lexical Analytics")
                sb.appendLine()
                sb.appendLine("- **Total Significant Tokens**: $totalWords")
                sb.appendLine("- **Unique Vocabulary Size**: ${wordFreq.size} words")
                sb.appendLine()
                sb.appendLine("## 🔝 Most Frequent Conceptual Keywords")
                topWords.forEachIndexed { i, entry ->
                    sb.appendLine("${i + 1}. **${entry.key}** (${entry.value} occurrences)")
                }

                File(vaultDir, "Lexical Analytics.md").writeText(sb.toString())
                "✅ **Lexical Analysis Complete**: Processed $totalWords tokens across ${files.size} documents. Saved report to `[[Lexical Analytics]]`."
            }

            "export_vault_json" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                val jsonArr = JSONArray()
                val wikilinkPattern = Pattern.compile("\\[\\[(.*?)\\]\\]")

                for (f in files) {
                    val content = try { f.readText() } catch (e: Exception) { "" }
                    val outlinks = mutableListOf<String>()
                    val m = wikilinkPattern.matcher(content)
                    while (m.find()) {
                        m.group(1)?.split("|")?.first()?.trim()?.let { outlinks.add(it) }
                    }

                    val obj = JSONObject()
                        .put("title", f.nameWithoutExtension)
                        .put("path", f.relativeTo(vaultDir).path)
                        .put("sizeBytes", f.length())
                        .put("lastModified", f.lastModified())
                        .put("outlinks", JSONArray(outlinks))
                    jsonArr.put(obj)
                }

                val exportFile = File(vaultDir, "vault_export.json")
                exportFile.writeText(jsonArr.toString(2))
                "✅ **JSON Manifest Exported**: Serialized graph data for ${files.size} notes to `${exportFile.name}` (${exportFile.length() / 1024} KB)."
            }

            "wikilink_normalizer" -> {
                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                var cleanedFiles = 0
                val pattern = Pattern.compile("\\[\\[\\s*(.*?)\\s*\\]\\]")

                for (f in files) {
                    val original = try { f.readText() } catch (e: Exception) { "" }
                    val matcher = pattern.matcher(original)
                    val sb = StringBuffer()
                    var changed = false
                    while (matcher.find()) {
                        val inner = matcher.group(1) ?: ""
                        val normalized = inner.trim()
                        if (inner != normalized) changed = true
                        matcher.appendReplacement(sb, "[[$normalized]]")
                    }
                    matcher.appendTail(sb)
                    if (changed) {
                        f.writeText(sb.toString())
                        cleanedFiles++
                    }
                }
                "✅ **Wikilink Normalization Complete**: Cleaned up whitespace in link targets across $cleanedFiles documents."
            }

            "regex_replace" -> {
                val patternStr = params.optString("pattern")
                val replacement = params.optString("replacement", "")
                if (patternStr.isBlank()) {
                    return@withContext "⚠️ Regex replacement failed: 'pattern' parameter is empty."
                }

                val regex = try { Regex(patternStr) } catch (e: Exception) {
                    return@withContext "⚠️ Invalid regex syntax: ${e.message}"
                }

                val files = vaultDir.walkTopDown().filter { it.isFile && it.extension == "md" }.toList()
                var modifiedCount = 0
                for (f in files) {
                    val original = try { f.readText() } catch (e: Exception) { "" }
                    val replaced = original.replace(regex, replacement)
                    if (replaced != original) {
                        f.writeText(replaced)
                        modifiedCount++
                    }
                }
                "✅ **Regex Batch Replace Complete**: Pattern `$patternStr` replaced across $modifiedCount files."
            }

            else -> {
                val scriptsDir = File(vaultDir, ".scripts")
                val ruleEngine = com.example.data.scripts.DynamicRuleEngine()
                val customRules = ruleEngine.loadAllScripts(scriptsDir)
                val matching = customRules.find { it.id.equals(scriptId, ignoreCase = true) || File(it.filePath).name.equals(scriptId, ignoreCase = true) }
                if (matching != null) {
                    val summary = ruleEngine.executeRuleAcrossVault(matching, vaultDir)
                    "✅ **Dynamic Script [${matching.name}] Executed**: Examined ${summary.filesExamined} files, modified ${summary.filesModified} notes. ${summary.details}"
                } else {
                    executeHook(scriptId, vaultDir, params)
                }
            }
        }
    }

    // Standard Automation Hooks & Scripts
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
                "⚠️ Unknown automation hook: '$hookName'. Supported hooks: `backup_vault`, `generate_moc_index`, `clean_empty_files`, `todo_aggregator`, `frontmatter_injector`, `word_frequency_analyzer`, `export_vault_json`, `wikilink_normalizer`."
            }
        }
    }
}
