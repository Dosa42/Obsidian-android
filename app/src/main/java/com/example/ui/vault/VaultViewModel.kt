package com.example.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.config.VaultAuthConfig
import com.example.data.filesystem.VaultFileSystemManager
import com.example.data.gemini.GeminiService
import com.example.data.local.VaultDatabase
import com.example.data.model.BacklinkItem
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModel
import com.example.data.model.ToolExecutionResult
import com.example.data.model.VaultNote
import com.example.data.repository.SearchResult
import com.example.data.repository.VaultGraphData
import com.example.data.repository.VaultRepository
import com.example.data.skills.AndroidKnowledgeTopic
import com.example.data.skills.AndroidSkillDefinition
import com.example.data.skills.DeviceTelemetry
import com.example.data.skills.DisplayMetricsInfo
import com.example.data.skills.HardwareSensorsInfo
import com.example.data.skills.RuntimeJvmInfo
import com.example.data.skills.StorageAudit
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

enum class VaultTab {
    EXPLORER,
    EDITOR,
    GRAPH,
    SEARCH,
    WIKI
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val database = VaultDatabase.getDatabase(application)
    private val fileSystemManager = VaultFileSystemManager(application, database.vaultDao())
    private val repository = VaultRepository(database.vaultDao(), fileSystemManager, application)
    private val geminiService = GeminiService()

    val authConfig: StateFlow<VaultAuthConfig> = repository.authConfig

    val allNotes: StateFlow<List<VaultNote>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultAbsolutePath: String get() = repository.vaultAbsolutePath

    val bookmarkedNotes: StateFlow<List<VaultNote>> = repository.bookmarkedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeNote = MutableStateFlow<VaultNote?>(null)
    val activeNote: StateFlow<VaultNote?> = _activeNote.asStateFlow()

    private val _activeTab = MutableStateFlow(VaultTab.EXPLORER)
    val activeTab: StateFlow<VaultTab> = _activeTab.asStateFlow()

    private val _isLivePreview = MutableStateFlow(true)
    val isLivePreview: StateFlow<Boolean> = _isLivePreview.asStateFlow()

    private val _showBacklinks = MutableStateFlow(false)
    val showBacklinks: StateFlow<Boolean> = _showBacklinks.asStateFlow()

    private val _linkedMentions = MutableStateFlow<List<BacklinkItem>>(emptyList())
    val linkedMentions: StateFlow<List<BacklinkItem>> = _linkedMentions.asStateFlow()

    private val _unlinkedMentions = MutableStateFlow<List<BacklinkItem>>(emptyList())
    val unlinkedMentions: StateFlow<List<BacklinkItem>> = _unlinkedMentions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _selectedTagFilter = MutableStateFlow<String?>(null)
    val selectedTagFilter: StateFlow<String?> = _selectedTagFilter.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // Graph Data
    private val _graphData = MutableStateFlow(VaultGraphData(emptyList(), emptyList()))
    val graphData: StateFlow<VaultGraphData> = _graphData.asStateFlow()

    // LLM Wiki Chat - backed by .chat/chat_history.json
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatModel = MutableStateFlow(GeminiModel.FLASH_3_5)
    val chatModel: StateFlow<GeminiModel> = _chatModel.asStateFlow()

    val allTags: StateFlow<List<String>> = allNotes.map { notes ->
        notes.flatMap { it.tags }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<String>> = allNotes.map { notes ->
        notes.map { it.folder }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initialize()
            // Load chat history from /storage/emulated/0/Download/ObsidianVault/.chat/chat_history.json
            val loadedChat = repository.loadChatHistory()
            _chatMessages.value = loadedChat
            syncFilesystem()
        }

        viewModelScope.launch {
            allNotes.collect { notes ->
                _graphData.value = repository.buildGraph(notes)
                // If active note is selected, refresh its reference
                _activeNote.value?.let { current ->
                    notes.find { it.id == current.id }?.let { updated ->
                        _activeNote.value = updated
                        updateBacklinks(updated, notes)
                    }
                }
            }
        }
    }

    fun selectTab(tab: VaultTab) {
        _activeTab.value = tab
    }

    fun toggleLivePreview() {
        _isLivePreview.value = !_isLivePreview.value
    }

    fun toggleBacklinks() {
        _showBacklinks.value = !_showBacklinks.value
    }

    fun setChatModel(model: GeminiModel) {
        _chatModel.value = model
    }

    fun openNote(note: VaultNote) {
        _activeNote.value = note
        _activeTab.value = VaultTab.EDITOR
        updateBacklinks(note, allNotes.value)
    }

    fun openNoteByTitle(title: String) {
        viewModelScope.launch {
            val existing = repository.getNoteByTitle(title)
            if (existing != null) {
                openNote(existing)
            } else {
                // Auto create the note on filesystem
                val newNote = repository.createNote(
                    title = title,
                    folder = "Root",
                    content = "# $title\n\n#concept\n\nNewly created wiki node linked from obsidian vault."
                )
                openNote(newNote)
            }
        }
    }

    private fun updateBacklinks(note: VaultNote, notes: List<VaultNote>) {
        viewModelScope.launch {
            val (linked, unlinked) = repository.computeBacklinks(note, notes)
            _linkedMentions.value = linked
            _unlinkedMentions.value = unlinked
        }
    }

    fun saveActiveNote(content: String) {
        val current = _activeNote.value ?: return
        viewModelScope.launch {
            val updated = repository.saveNote(current, content)
            _activeNote.value = updated
            updateBacklinks(updated, allNotes.value)
        }
    }

    fun createNote(title: String, folder: String = "Root", content: String = "") {
        viewModelScope.launch {
            val note = repository.createNote(title, folder, content)
            openNote(note)
            syncFilesystem()
        }
    }

    fun deleteActiveNote() {
        val current = _activeNote.value ?: return
        viewModelScope.launch {
            repository.deleteNote(current)
            _activeNote.value = null
            _activeTab.value = VaultTab.EXPLORER
        }
    }

    fun toggleBookmark(note: VaultNote) {
        viewModelScope.launch {
            repository.toggleBookmark(note)
        }
    }

    fun createFolder(folderName: String) {
        viewModelScope.launch {
            repository.createFolder(folderName)
            syncFilesystem()
        }
    }

    fun syncFilesystem() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Indexing /storage/emulated/0/Download/ObsidianVault..."
            try {
                val count = repository.syncVault()
                _syncMessage.value = "Storage synced: $count markdown notes indexed."
            } catch (e: Exception) {
                _syncMessage.value = "Sync notice: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            var pool = allNotes.value
            _selectedTagFilter.value?.let { tag ->
                pool = pool.filter { it.tags.contains(tag) }
            }
            _searchResults.value = repository.semanticSearch(query, pool)
        }
    }

    fun setTagFilter(tag: String?) {
        _selectedTagFilter.value = if (_selectedTagFilter.value == tag) null else tag
        onSearchQueryChanged(_searchQuery.value)
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            val success = repository.updateApiKey(apiKey)
            if (success) {
                showToast("API Key saved to .auth/vault_auth_config.json")
            } else {
                showToast("Failed saving API Key to disk")
            }
        }
    }

    fun savePersona(name: String, title: String, prompt: String, temp: Double) {
        viewModelScope.launch {
            val success = repository.updatePersona(name, title, prompt, temp)
            if (success) {
                showToast("Persona saved to .auth/vault_auth_config.json")
            } else {
                showToast("Failed saving Persona to disk")
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            val resetList = repository.clearChatHistory()
            _chatMessages.value = resetList
            showToast("Chat history reset")
        }
    }

    fun exportChatMarkdown() {
        viewModelScope.launch {
            val file = repository.exportChatToMarkdown(_chatMessages.value)
            showToast("Exported transcript to ${file.name}")
            syncFilesystem()
        }
    }

    fun exportBackupZip() {
        viewModelScope.launch {
            try {
                val zip = repository.exportVaultZip()
                showToast("Backup created: ${zip.name}")
            } catch (e: Exception) {
                showToast("Backup failed: ${e.message}")
            }
        }
    }

    fun sendChatMessage(userText: String, askVault: Boolean = false) {
        if (userText.isBlank()) return
        val userMsg = ChatMessage(role = "user", text = userText)
        val updatedList = _chatMessages.value + userMsg
        _chatMessages.value = updatedList
        _isChatLoading.value = true

        viewModelScope.launch {
            repository.saveChatHistory(updatedList)

            val contextNotes = if (askVault) {
                val results = repository.semanticSearch(userText, allNotes.value)
                results.take(4).map { it.note }
            } else {
                emptyList()
            }

            val result = geminiService.generateResponse(
                messages = _chatMessages.value.filter { it.role != "system" },
                userPrompt = userText,
                model = _chatModel.value,
                vaultNotesContext = contextNotes,
                authConfig = authConfig.value
            )

            result.onSuccess { reply ->
                val cited = contextNotes.map { it.title }
                val executedTools = mutableListOf<ToolExecutionResult>()

                // Parse and execute system developer tool calls
                val toolRegex = "```tool_call\\s*\\n?([\\s\\S]*?)\\n?```".toRegex()
                val toolMatches = toolRegex.findAll(reply).toList()

                for (match in toolMatches) {
                    val jsonRaw = match.groupValues[1].trim()
                    try {
                        val json = JSONObject(jsonRaw)
                        val action = json.optString("action")
                        when (action) {
                            "create_note" -> {
                                val title = json.optString("title")
                                val folder = json.optString("folder", "Root")
                                val content = json.optString("content", "")
                                if (title.isNotBlank()) {
                                    repository.createOrUpdateNoteByTitle(title, folder, content)
                                    executedTools.add(
                                        ToolExecutionResult(
                                            action = "create_note",
                                            target = title,
                                            details = "Saved markdown file to $vaultAbsolutePath/$folder"
                                        )
                                    )
                                }
                            }
                            "update_note" -> {
                                val title = json.optString("title")
                                val content = json.optString("content", "")
                                if (title.isNotBlank()) {
                                    repository.createOrUpdateNoteByTitle(title, "Root", content)
                                    executedTools.add(
                                        ToolExecutionResult(
                                            action = "update_note",
                                            target = title,
                                            details = "Updated markdown file on disk"
                                        )
                                    )
                                }
                            }
                            "delete_note" -> {
                                val title = json.optString("title")
                                if (title.isNotBlank()) {
                                    val deleted = repository.deleteNoteByTitle(title)
                                    executedTools.add(
                                        ToolExecutionResult(
                                            action = "delete_note",
                                            target = title,
                                            details = if (deleted) "Deleted file from $vaultAbsolutePath" else "Note not found in vault",
                                            success = deleted
                                        )
                                    )
                                }
                            }
                            "create_folder" -> {
                                val folder = json.optString("folder")
                                if (folder.isNotBlank()) {
                                    repository.createFolder(folder)
                                    executedTools.add(
                                        ToolExecutionResult(
                                            action = "create_folder",
                                            target = folder,
                                            details = "Created directory at $vaultAbsolutePath/$folder"
                                        )
                                    )
                                }
                            }
                            "refactor_links" -> {
                                val oldTitle = json.optString("old_title")
                                val newTitle = json.optString("new_title")
                                if (oldTitle.isNotBlank() && newTitle.isNotBlank()) {
                                    val count = repository.refactorWikilinks(oldTitle, newTitle)
                                    executedTools.add(
                                        ToolExecutionResult(
                                            action = "refactor_links",
                                            target = "$oldTitle ➔ $newTitle",
                                            details = "Refactored $count files on external storage"
                                        )
                                    )
                                }
                            }
                            "run_diagnostic" -> {
                                val diag = repository.runVaultDiagnostic()
                                executedTools.add(
                                    ToolExecutionResult(
                                        action = "run_diagnostic",
                                        target = "Storage Diagnostic",
                                        details = diag
                                    )
                                )
                            }
                            "android_skill" -> {
                                val skill = json.optString("skill")
                                when (skill) {
                                    "get_device_telemetry" -> {
                                        val telem = repository.getDeviceTelemetry()
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Device Telemetry",
                                                details = "OS: ${telem.osVersion} (SDK ${telem.sdkInt}) | RAM: ${telem.ramUsagePercent}% (${telem.availableRamMb}MB free) | Battery: ${telem.batteryPercent}% (${if (telem.isCharging) "Charging" else "Discharging"}) | Net: ${telem.networkType}"
                                            )
                                        )
                                    }
                                    "get_storage_audit" -> {
                                        val audit = repository.getStorageAudit()
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Storage Audit",
                                                details = "Vault: ${audit.totalFiles} files (${audit.totalSizeBytes / 1024} KB) | Download Partition Free: ${audit.freeSpaceBytes / (1024 * 1024 * 1024)} GB"
                                            )
                                        )
                                    }
                                    "clipboard_write" -> {
                                        val text = json.optString("text")
                                        val ok = repository.copyToClipboard("Venice", text)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Clipboard Write",
                                                details = if (ok) "Copied ${text.length} chars to Android clipboard" else "Clipboard write failed",
                                                success = ok
                                            )
                                        )
                                    }
                                    "trigger_toast" -> {
                                        val msg = json.optString("message", "Venice AI Notification")
                                        repository.showToast(msg)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "System Toast",
                                                details = "Toast message dispatched: \"$msg\""
                                            )
                                        )
                                    }
                                    "trigger_haptic" -> {
                                        val ms = json.optLong("duration_ms", 50L)
                                        repository.triggerHaptic(ms)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Haptic Vibration",
                                                details = "Triggered ${ms}ms tactile vibration feedback"
                                            )
                                        )
                                    }
                                    "execute_hook" -> {
                                        val hook = json.optString("hook_name")
                                        val res = repository.executeSkillHook(hook, json)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Hook: $hook",
                                                details = res
                                            )
                                        )
                                    }
                                    "get_display_metrics" -> {
                                        val dm = repository.getDisplayMetrics()
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Display Metrics",
                                                details = "Resolution: ${dm.widthPx}x${dm.heightPx} px | DPI: ${dm.densityDpi} (Scale ${dm.densityScale}x) | Orientation: ${dm.orientation}"
                                            )
                                        )
                                    }
                                    "get_runtime_jvm" -> {
                                        val jvm = repository.getRuntimeJvmInfo()
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "JVM Runtime",
                                                details = "Heap: ${jvm.jvmHeapTotalMb}MB (Free ${jvm.jvmHeapFreeMb}MB / Max ${jvm.jvmHeapMaxMb}MB) | Active Threads: ${jvm.activeThreadCount} | Cores: ${jvm.availableProcessors}"
                                            )
                                        )
                                    }
                                    "get_hardware_sensors" -> {
                                        val sensors = repository.getHardwareSensorsInfo()
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Hardware Sensors",
                                                details = "Sensors: ${sensors.totalSensorsFound} found (Accel: ${sensors.hasAccelerometer}, Gyro: ${sensors.hasGyroscope}, Light: ${sensors.hasLightSensor}, Prox: ${sensors.hasProximitySensor})"
                                            )
                                        )
                                    }
                                    "share_content" -> {
                                        val text = json.optString("text")
                                        val title = json.optString("title", "Share Note")
                                        repository.shareContent(text, title)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "System Share",
                                                details = "Dispatched native Android share sheet for: \"$title\""
                                            )
                                        )
                                    }
                                    "execute_dynamic_script" -> {
                                        val script = json.optString("script")
                                        val res = repository.executeDynamicScript(script, json)
                                        executedTools.add(
                                            ToolExecutionResult(
                                                action = "android_skill",
                                                target = "Dynamic Script: $script",
                                                details = res
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        executedTools.add(
                            ToolExecutionResult(
                                action = "tool_call_error",
                                target = "System Execution",
                                details = "Execution error: ${e.message}",
                                success = false
                            )
                        )
                    }
                }

                // If tools modified the vault, resync database
                if (executedTools.any { (it.action != "run_diagnostic" && it.action != "android_skill" && it.success) || (it.action == "android_skill" && (it.target.contains("Hook") || it.target.contains("Script")) && it.success) }) {
                    repository.syncVault()
                }

                // Strip raw tool blocks from reply text for a clean presentation
                val cleanText = reply.replace(toolRegex, "").trim()

                val modelMsg = ChatMessage(
                    role = "model",
                    text = if (cleanText.isNotBlank()) cleanText else "System Developer commands executed successfully.",
                    citedNotes = cited,
                    executedTools = executedTools
                )
                val finalizedList = _chatMessages.value + modelMsg
                _chatMessages.value = finalizedList
                repository.saveChatHistory(finalizedList)
            }.onFailure { err ->
                val errorMsg = ChatMessage(
                    role = "model",
                    text = "⚠️ **Error communicating with Gemini (${_chatModel.value.displayName})**\n\n${err.message}\n\n*Configure your GEMINI_API_KEY in the Vault Storage & Auth Settings.*"
                )
                val finalizedList = _chatMessages.value + errorMsg
                _chatMessages.value = finalizedList
                repository.saveChatHistory(finalizedList)
            }

            _isChatLoading.value = false
        }
    }

    fun triggerSystemDiagnostic() {
        viewModelScope.launch {
            _isChatLoading.value = true
            val diagReport = repository.runVaultDiagnostic()
            val diagMsg = ChatMessage(
                role = "model",
                text = diagReport,
                executedTools = listOf(
                    ToolExecutionResult(
                        action = "run_diagnostic",
                        target = "System Health Audit",
                        details = "Executed automated integrity check across $vaultAbsolutePath"
                    )
                )
            )
            val updated = _chatMessages.value + diagMsg
            _chatMessages.value = updated
            repository.saveChatHistory(updated)
            _isChatLoading.value = false
        }
    }

    fun synthesizeWikiTopic(topic: String) {
        if (topic.isBlank()) return
        val userMsg = ChatMessage(role = "user", text = "Synthesize an authoritative Wiki Node for: **$topic**")
        val withUser = _chatMessages.value + userMsg
        _chatMessages.value = withUser
        _isChatLoading.value = true

        viewModelScope.launch {
            repository.saveChatHistory(withUser)
            val relatedNotes = repository.semanticSearch(topic, allNotes.value).map { it.note }
            val result = geminiService.synthesizeWikiNode(topic, relatedNotes, _chatModel.value, authConfig.value)

            result.onSuccess { article ->
                val modelMsg = ChatMessage(
                    role = "model",
                    text = article,
                    citedNotes = relatedNotes.map { it.title },
                    isSynthesizing = true
                )
                val updated = _chatMessages.value + modelMsg
                _chatMessages.value = updated
                repository.saveChatHistory(updated)
            }.onFailure { err ->
                val errorMsg = ChatMessage(
                    role = "model",
                    text = "⚠️ **Synthesis failed**: ${err.message}"
                )
                val updated = _chatMessages.value + errorMsg
                _chatMessages.value = updated
                repository.saveChatHistory(updated)
            }
            _isChatLoading.value = false
        }
    }

    fun suggestLinksForActiveNote() {
        val current = _activeNote.value ?: return
        _isChatLoading.value = true
        _activeTab.value = VaultTab.WIKI

        val promptMsg = ChatMessage(role = "user", text = "Suggest semantic [[wikilinks]] for note: **${current.title}**")
        val withUser = _chatMessages.value + promptMsg
        _chatMessages.value = withUser

        viewModelScope.launch {
            repository.saveChatHistory(withUser)
            val allTitles = allNotes.value.map { it.title }
            val result = geminiService.suggestLinks(current.title, current.content, allTitles, authConfig.value)

            result.onSuccess { suggestions ->
                val modelMsg = ChatMessage(
                    role = "model",
                    text = suggestions
                )
                val updated = _chatMessages.value + modelMsg
                _chatMessages.value = updated
                repository.saveChatHistory(updated)
            }.onFailure { err ->
                val errorMsg = ChatMessage(
                    role = "model",
                    text = "⚠️ **Link suggestion failed**: ${err.message}"
                )
                val updated = _chatMessages.value + errorMsg
                _chatMessages.value = updated
                repository.saveChatHistory(updated)
            }
            _isChatLoading.value = false
        }
    }

    fun saveMessageAsNote(message: ChatMessage, customTitle: String = "") {
        viewModelScope.launch {
            val cleanTitle = if (customTitle.isNotBlank()) {
                customTitle
            } else {
                val firstLine = message.text.lines().firstOrNull { it.startsWith("#") }
                if (firstLine != null) {
                    firstLine.replace("#", "").trim()
                } else {
                    "Wiki Synthesis ${System.currentTimeMillis() % 10000}"
                }
            }
            val note = repository.createNote(
                title = cleanTitle,
                folder = "Wiki",
                content = message.text
            )
            openNote(note)
        }
    }

    val skillsCatalog: List<AndroidSkillDefinition> get() = repository.skillsCatalog

    private val _deviceTelemetry = MutableStateFlow<DeviceTelemetry?>(null)
    val deviceTelemetry: StateFlow<DeviceTelemetry?> = _deviceTelemetry.asStateFlow()

    private val _storageAudit = MutableStateFlow<StorageAudit?>(null)
    val storageAudit: StateFlow<StorageAudit?> = _storageAudit.asStateFlow()

    fun refreshDeviceTelemetry() {
        _deviceTelemetry.value = repository.getDeviceTelemetry()
    }

    fun loadStorageAudit() {
        viewModelScope.launch {
            _storageAudit.value = repository.getStorageAudit()
        }
    }

    fun executeAutomationHook(hookName: String) {
        viewModelScope.launch {
            _isChatLoading.value = true
            val output = repository.executeSkillHook(hookName)
            val msg = ChatMessage(
                role = "model",
                text = output,
                executedTools = listOf(
                    ToolExecutionResult(
                        action = "android_skill",
                        target = "Hook: $hookName",
                        details = output
                    )
                )
            )
            val updated = _chatMessages.value + msg
            _chatMessages.value = updated
            repository.saveChatHistory(updated)
            if (hookName in listOf("generate_moc_index", "clean_empty_files")) {
                repository.syncVault()
            }
            _isChatLoading.value = false
        }
    }

    fun copyToClipboard(label: String, text: String): Boolean {
        return repository.copyToClipboard(label, text)
    }

    fun showToast(message: String) {
        repository.showToast(message)
    }

    fun triggerHaptic(durationMs: Long = 50) {
        repository.triggerHaptic(durationMs)
    }

    val androidKnowledgeBase: List<AndroidKnowledgeTopic> get() = repository.androidKnowledgeBase

    private val _displayMetrics = MutableStateFlow<DisplayMetricsInfo?>(null)
    val displayMetrics: StateFlow<DisplayMetricsInfo?> = _displayMetrics.asStateFlow()

    private val _runtimeJvmInfo = MutableStateFlow<RuntimeJvmInfo?>(null)
    val runtimeJvmInfo: StateFlow<RuntimeJvmInfo?> = _runtimeJvmInfo.asStateFlow()

    private val _hardwareSensorsInfo = MutableStateFlow<HardwareSensorsInfo?>(null)
    val hardwareSensorsInfo: StateFlow<HardwareSensorsInfo?> = _hardwareSensorsInfo.asStateFlow()

    fun refreshAllTelemetry() {
        _deviceTelemetry.value = repository.getDeviceTelemetry()
        _displayMetrics.value = repository.getDisplayMetrics()
        _runtimeJvmInfo.value = repository.getRuntimeJvmInfo()
        _hardwareSensorsInfo.value = repository.getHardwareSensorsInfo()
        viewModelScope.launch {
            _storageAudit.value = repository.getStorageAudit()
        }
    }

    fun executeDynamicScript(scriptId: String, params: JSONObject = JSONObject()) {
        viewModelScope.launch {
            _isChatLoading.value = true
            val output = repository.executeDynamicScript(scriptId, params)
            val msg = ChatMessage(
                role = "model",
                text = output,
                executedTools = listOf(
                    ToolExecutionResult(
                        action = "android_skill",
                        target = "Script: $scriptId",
                        details = output
                    )
                )
            )
            val updated = _chatMessages.value + msg
            _chatMessages.value = updated
            repository.saveChatHistory(updated)
            repository.syncVault()
            _isChatLoading.value = false
        }
    }

    fun shareNote(title: String, content: String) {
        repository.shareContent(content, title)
    }
}
