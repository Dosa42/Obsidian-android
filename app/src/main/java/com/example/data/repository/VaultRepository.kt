package com.example.data.repository

import android.content.Context
import com.example.data.chat.VaultChatStorageManager
import com.example.data.config.VaultAuthConfig
import com.example.data.config.VaultAuthConfigManager
import com.example.data.filesystem.VaultFileSystemManager
import com.example.data.local.VaultDao
import com.example.data.model.BacklinkItem
import com.example.data.model.ChatMessage
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.data.model.VaultNote
import com.example.data.scripts.DynamicScriptRule
import com.example.data.scripts.ScriptExecutionSummary
import com.example.data.skills.AndroidKnowledgeTopic
import com.example.data.skills.AndroidSkillDefinition
import com.example.data.skills.AndroidSkillsManager
import com.example.data.skills.DeviceTelemetry
import com.example.data.skills.DisplayMetricsInfo
import com.example.data.skills.HardwareSensorsInfo
import com.example.data.skills.RuntimeJvmInfo
import com.example.data.skills.StorageAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.regex.Pattern

data class SearchResult(
    val note: VaultNote,
    val score: Double,
    val snippet: String,
    val matchedTerms: List<String>
)

data class VaultGraphData(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>
)

class VaultRepository(
    private val vaultDao: VaultDao,
    val fileSystemManager: VaultFileSystemManager,
    private val context: Context
) {
    val authConfigManager = VaultAuthConfigManager(context)
    val chatStorageManager = VaultChatStorageManager(context)
    val skillsManager = AndroidSkillsManager(context)

    val skillsCatalog: List<AndroidSkillDefinition> get() = skillsManager.skillsCatalog
    val androidKnowledgeBase: List<AndroidKnowledgeTopic> get() = skillsManager.androidKnowledgeBase

    val authConfig: StateFlow<VaultAuthConfig> get() = authConfigManager.configFlow

    fun getDynamicScripts(): List<DynamicScriptRule> = fileSystemManager.getDynamicScripts()

    suspend fun executeDynamicScriptFile(scriptId: String): ScriptExecutionSummary {
        return fileSystemManager.executeDynamicScriptFile(scriptId)
    }

    suspend fun reloadAuthConfig(): VaultAuthConfig {
        return authConfigManager.reloadFromDisk()
    }

    fun getDeviceTelemetry(): DeviceTelemetry = skillsManager.getDeviceTelemetry()
    fun getDisplayMetrics(): DisplayMetricsInfo = skillsManager.getDisplayMetrics()
    fun getRuntimeJvmInfo(): RuntimeJvmInfo = skillsManager.getRuntimeJvmInfo()
    fun getHardwareSensorsInfo(): HardwareSensorsInfo = skillsManager.getHardwareSensorsInfo()
    suspend fun getStorageAudit(): StorageAudit = skillsManager.getStorageAudit(fileSystemManager.vaultRoot)
    fun copyToClipboard(label: String, text: String): Boolean = skillsManager.copyToClipboard(label, text)
    fun readFromClipboard(): String = skillsManager.readClipboard() ?: ""
    fun getAndroidKnowledgeTopic(query: String): AndroidKnowledgeTopic? {
        return skillsManager.androidKnowledgeBase.find { 
            it.id.equals(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true) 
        }
    }
    fun showToast(msg: String) = skillsManager.showToast(msg)
    fun triggerHaptic(durationMs: Long = 50) = skillsManager.triggerHaptic(durationMs)
    fun shareContent(text: String, title: String = "Share Note") = skillsManager.shareContent(text, title)

    suspend fun executeSkillHook(hookName: String, params: JSONObject = JSONObject()): String {
        return skillsManager.executeHook(hookName, fileSystemManager.vaultRoot, params)
    }

    suspend fun executeDynamicScript(scriptId: String, params: JSONObject = JSONObject()): String {
        return skillsManager.executeDynamicScript(scriptId, fileSystemManager.vaultRoot, params)
    }

    val allNotes: Flow<List<VaultNote>> = vaultDao.getAllNotes()
    val bookmarkedNotes: Flow<List<VaultNote>> = vaultDao.getBookmarkedNotes()
    val vaultAbsolutePath: String get() = fileSystemManager.vaultAbsolutePath
    val vaultRoot: File get() = fileSystemManager.vaultRoot

    suspend fun initialize() {
        authConfigManager.initialize()
        fileSystemManager.initializeDefaultVaultIfEmpty()
    }

    suspend fun loadChatHistory(): List<ChatMessage> {
        return chatStorageManager.loadChatHistory()
    }

    suspend fun saveChatHistory(messages: List<ChatMessage>): Boolean {
        return chatStorageManager.saveChatHistory(messages)
    }

    suspend fun clearChatHistory(): List<ChatMessage> {
        return chatStorageManager.clearChatHistory()
    }

    suspend fun exportChatToMarkdown(messages: List<ChatMessage>): File {
        val file = chatStorageManager.exportChatToMarkdownFile(messages)
        syncVault()
        return file
    }

    suspend fun updateApiKey(apiKey: String): Boolean {
        return authConfigManager.updateApiKey(apiKey)
    }

    suspend fun updatePersona(
        name: String,
        title: String,
        prompt: String,
        temperature: Double
    ): Boolean {
        return authConfigManager.updatePersona(name, title, prompt, temperature)
    }

    suspend fun exportVaultZip(): File {
        return fileSystemManager.exportVaultZipArchive()
    }

    suspend fun syncVault(): Int {
        return fileSystemManager.syncFilesystemToDatabase()
    }

    suspend fun saveNote(note: VaultNote, content: String): VaultNote {
        return fileSystemManager.saveNote(note, content)
    }

    suspend fun createNote(title: String, folder: String = "Root", content: String = ""): VaultNote {
        return fileSystemManager.createNote(title, folder, content)
    }

    suspend fun deleteNote(note: VaultNote) {
        fileSystemManager.deleteNote(note)
    }

    suspend fun toggleBookmark(note: VaultNote) = withContext(Dispatchers.IO) {
        val updated = note.copy(isBookmarked = !note.isBookmarked)
        vaultDao.updateNote(updated)
    }

    suspend fun getNoteByTitle(title: String): VaultNote? = withContext(Dispatchers.IO) {
        vaultDao.getNoteByTitle(title)
    }

    suspend fun createFolder(folderName: String): Boolean {
        return fileSystemManager.createFolder(folderName)
    }

    suspend fun createOrUpdateNoteByTitle(title: String, folder: String = "Root", content: String): VaultNote {
        return fileSystemManager.createOrUpdateNoteByTitle(title, folder, content)
    }

    suspend fun deleteNoteByTitle(title: String): Boolean {
        return fileSystemManager.deleteNoteByTitle(title)
    }

    suspend fun refactorWikilinks(oldTitle: String, newTitle: String): Int {
        return fileSystemManager.refactorWikilinks(oldTitle, newTitle)
    }

    suspend fun runVaultDiagnostic(): String {
        return fileSystemManager.runVaultDiagnostic()
    }

    // Semantic search with token scoring & snippet extraction
    suspend fun semanticSearch(query: String, notes: List<VaultNote>): List<SearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext emptyList()

        val terms = query.lowercase().split("\\s+".toRegex()).filter { it.length > 1 }
        if (terms.isEmpty()) return@withContext emptyList()

        val results = mutableListOf<SearchResult>()

        for (note in notes) {
            var score = 0.0
            val lowerTitle = note.title.lowercase()
            val lowerContent = note.content.lowercase()
            val lowerTags = note.tags.map { it.lowercase() }
            val matched = mutableListOf<String>()

            for (term in terms) {
                var termFound = false
                if (lowerTitle.contains(term)) {
                    score += 15.0
                    termFound = true
                }
                if (lowerTags.any { it.contains(term) }) {
                    score += 10.0
                    termFound = true
                }
                val occurrences = countOccurrences(lowerContent, term)
                if (occurrences > 0) {
                    score += occurrences * 2.5
                    termFound = true
                }
                if (termFound) matched.add(term)
            }

            if (score > 0) {
                val snippet = extractSnippet(note.content, terms)
                results.add(SearchResult(note, score, snippet, matched))
            }
        }

        results.sortedByDescending { it.score }
    }

    private fun countOccurrences(text: String, word: String): Int {
        var count = 0
        var idx = 0
        while (text.indexOf(word, idx).also { idx = it } != -1) {
            count++
            idx += word.length
        }
        return count
    }

    private fun extractSnippet(content: String, terms: List<String>): String {
        val lower = content.lowercase()
        var bestIndex = -1
        for (term in terms) {
            val idx = lower.indexOf(term)
            if (idx != -1 && (bestIndex == -1 || idx < bestIndex)) {
                bestIndex = idx
            }
        }
        if (bestIndex == -1) {
            return content.take(160).replace("\n", " ").trim()
        }
        val start = (bestIndex - 60).coerceAtLeast(0)
        val end = (bestIndex + 100).coerceAtMost(content.length)
        var snip = content.substring(start, end).replace("\n", " ").trim()
        if (start > 0) snip = "...$snip"
        if (end < content.length) snip = "$snip..."
        return snip
    }

    // Compute Backlinks for a given note
    suspend fun computeBacklinks(targetNote: VaultNote, allNotes: List<VaultNote>): Pair<List<BacklinkItem>, List<BacklinkItem>> = withContext(Dispatchers.Default) {
        val linkedMentions = mutableListOf<BacklinkItem>()
        val unlinkedMentions = mutableListOf<BacklinkItem>()

        val targetTitle = targetNote.title
        val escapedTitle = Pattern.quote(targetTitle)
        val explicitPattern = Pattern.compile("\\[\\[$escapedTitle(\\|.*?)?\\]\\]", Pattern.CASE_INSENSITIVE)
        val plainPattern = Pattern.compile("\\b$escapedTitle\\b", Pattern.CASE_INSENSITIVE)

        for (other in allNotes) {
            if (other.id == targetNote.id) continue

            if (other.outlinks.any { it.equals(targetTitle, ignoreCase = true) } || explicitPattern.matcher(other.content).find()) {
                val snippet = extractSnippet(other.content, listOf(targetTitle))
                linkedMentions.add(BacklinkItem(other, snippet, isExplicitLink = true))
            } else if (plainPattern.matcher(other.content).find()) {
                val snippet = extractSnippet(other.content, listOf(targetTitle))
                unlinkedMentions.add(BacklinkItem(other, snippet, isExplicitLink = false))
            }
        }

        Pair(linkedMentions, unlinkedMentions)
    }

    private val nodePositionCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Float, Float>>()

    fun updateCachedNodePositions(positions: Map<String, Pair<Float, Float>>) {
        nodePositionCache.putAll(positions)
    }

    // Build Graph Data with stable IDs, unresolved links, tags, and cached coordinates
    fun buildGraph(
        allNotes: List<VaultNote>,
        includeUnresolved: Boolean = true,
        includeTags: Boolean = true
    ): VaultGraphData {
        // Fast lookups for note resolution by path and title
        val pathToNote = allNotes.associateBy { it.path.lowercase() }
        val titleToNote = allNotes.associateBy { it.title.lowercase() }

        val degrees = mutableMapOf<String, Int>()
        val edges = mutableListOf<GraphEdge>()
        val unresolvedNodesMap = mutableMapOf<String, GraphNode>()
        val tagNodesMap = mutableMapOf<String, GraphNode>()

        // 1. Process all notes and edges
        for (note in allNotes) {
            val sourceId = note.path.ifBlank { note.title }
            degrees.putIfAbsent(sourceId, 0)

            for (outlink in note.outlinks) {
                val cleanLink = outlink.trim()
                if (cleanLink.isBlank()) continue

                val targetKey = cleanLink.lowercase()
                val targetNote = pathToNote[targetKey]
                    ?: pathToNote["$targetKey.md"]
                    ?: titleToNote[targetKey]

                if (targetNote != null) {
                    val targetId = targetNote.path.ifBlank { targetNote.title }
                    edges.add(GraphEdge(sourceId = sourceId, targetId = targetId, isResolved = true))
                    degrees[sourceId] = (degrees[sourceId] ?: 0) + 1
                    degrees[targetId] = (degrees[targetId] ?: 0) + 1
                } else if (includeUnresolved) {
                    val unresolvedId = "unresolved:$cleanLink"
                    edges.add(GraphEdge(sourceId = sourceId, targetId = unresolvedId, isResolved = false))
                    degrees[sourceId] = (degrees[sourceId] ?: 0) + 1
                    degrees[unresolvedId] = (degrees[unresolvedId] ?: 0) + 1

                    if (!unresolvedNodesMap.containsKey(unresolvedId)) {
                        unresolvedNodesMap[unresolvedId] = GraphNode(
                            id = unresolvedId,
                            title = cleanLink,
                            folder = "Unresolved",
                            nodeType = GraphNodeType.UNRESOLVED,
                            path = cleanLink
                        )
                    }
                }
            }

            // Optional Tag nodes and edges
            if (includeTags) {
                for (tag in note.tags) {
                    val cleanTag = tag.removePrefix("#").trim()
                    if (cleanTag.isBlank()) continue
                    val tagId = "tag:#$cleanTag"
                    edges.add(GraphEdge(sourceId = sourceId, targetId = tagId, isResolved = true))
                    degrees[sourceId] = (degrees[sourceId] ?: 0) + 1
                    degrees[tagId] = (degrees[tagId] ?: 0) + 1

                    if (!tagNodesMap.containsKey(tagId)) {
                        tagNodesMap[tagId] = GraphNode(
                            id = tagId,
                            title = "#$cleanTag",
                            folder = "Tags",
                            nodeType = GraphNodeType.TAG,
                            path = cleanTag
                        )
                    }
                }
            }
        }

        // 2. Assemble note nodes
        val allNodesList = mutableListOf<GraphNode>()
        val totalExpected = allNotes.size + unresolvedNodesMap.size + tagNodesMap.size
        val goldenAngle = 2.39996322972865332 // Math.PI * (3.0 - Math.sqrt(5.0))
        var placeIndex = 0

        for (note in allNotes) {
            val nodeId = note.path.ifBlank { note.title }
            val deg = degrees[nodeId] ?: 0
            val cachedPos = nodePositionCache[nodeId]

            val (initX, initY) = if (cachedPos != null) {
                cachedPos
            } else {
                val radius = 90f + Math.sqrt(placeIndex.toDouble()).toFloat() * 75f
                val theta = (placeIndex * goldenAngle).toFloat()
                val x = (Math.cos(theta.toDouble()) * radius).toFloat()
                val y = (Math.sin(theta.toDouble()) * radius).toFloat()
                nodePositionCache[nodeId] = Pair(x, y)
                Pair(x, y)
            }
            placeIndex++

            allNodesList.add(
                GraphNode(
                    id = nodeId,
                    title = note.title,
                    folder = note.folder,
                    degree = deg,
                    x = initX,
                    y = initY,
                    path = note.path,
                    nodeType = GraphNodeType.NOTE,
                    tags = note.tags
                )
            )
        }

        // 3. Assemble unresolved nodes
        for ((uId, uNode) in unresolvedNodesMap) {
            val deg = degrees[uId] ?: 0
            val cachedPos = nodePositionCache[uId]
            val (initX, initY) = if (cachedPos != null) {
                cachedPos
            } else {
                val radius = 120f + Math.sqrt(placeIndex.toDouble()).toFloat() * 80f
                val theta = (placeIndex * goldenAngle).toFloat()
                val x = (Math.cos(theta.toDouble()) * radius).toFloat()
                val y = (Math.sin(theta.toDouble()) * radius).toFloat()
                nodePositionCache[uId] = Pair(x, y)
                Pair(x, y)
            }
            placeIndex++

            allNodesList.add(
                uNode.copy(
                    degree = deg,
                    x = initX,
                    y = initY
                )
            )
        }

        // 4. Assemble tag nodes
        for ((tId, tNode) in tagNodesMap) {
            val deg = degrees[tId] ?: 0
            val cachedPos = nodePositionCache[tId]
            val (initX, initY) = if (cachedPos != null) {
                cachedPos
            } else {
                val radius = 100f + Math.sqrt(placeIndex.toDouble()).toFloat() * 70f
                val theta = (placeIndex * goldenAngle).toFloat()
                val x = (Math.cos(theta.toDouble()) * radius).toFloat()
                val y = (Math.sin(theta.toDouble()) * radius).toFloat()
                nodePositionCache[tId] = Pair(x, y)
                Pair(x, y)
            }
            placeIndex++

            allNodesList.add(
                tNode.copy(
                    degree = deg,
                    x = initX,
                    y = initY
                )
            )
        }

        return VaultGraphData(allNodesList, edges)
    }
}
