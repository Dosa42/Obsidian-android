package com.example.data.repository

import android.content.Context
import com.example.data.filesystem.VaultFileSystemManager
import com.example.data.local.VaultDao
import com.example.data.model.BacklinkItem
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.VaultNote
import com.example.data.skills.AndroidSkillDefinition
import com.example.data.skills.AndroidSkillsManager
import com.example.data.skills.DeviceTelemetry
import com.example.data.skills.StorageAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
    private val fileSystemManager: VaultFileSystemManager,
    private val context: Context
) {
    val skillsManager = AndroidSkillsManager(context)
    val skillsCatalog: List<AndroidSkillDefinition> get() = skillsManager.skillsCatalog

    fun getDeviceTelemetry(): DeviceTelemetry = skillsManager.getDeviceTelemetry()
    suspend fun getStorageAudit(): StorageAudit = skillsManager.getStorageAudit(fileSystemManager.vaultRoot)
    fun copyToClipboard(label: String, text: String): Boolean = skillsManager.copyToClipboard(label, text)
    fun showToast(msg: String) = skillsManager.showToast(msg)
    fun triggerHaptic(durationMs: Long = 50) = skillsManager.triggerHaptic(durationMs)
    suspend fun executeSkillHook(hookName: String, params: JSONObject = JSONObject()): String {
        return skillsManager.executeHook(hookName, fileSystemManager.vaultRoot, params)
    }

    val allNotes: Flow<List<VaultNote>> = vaultDao.getAllNotes()
    val bookmarkedNotes: Flow<List<VaultNote>> = vaultDao.getBookmarkedNotes()
    val vaultAbsolutePath: String get() = fileSystemManager.vaultAbsolutePath

    suspend fun initialize() {
        fileSystemManager.initializeDefaultVaultIfEmpty()
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

    // Build Graph Data
    fun buildGraph(allNotes: List<VaultNote>): VaultGraphData {
        val titleToNote = allNotes.associateBy { it.title.lowercase() }
        val degrees = mutableMapOf<String, Int>()
        val edges = mutableListOf<GraphEdge>()

        // Initialize degree count
        allNotes.forEach { degrees[it.title] = 0 }

        for (note in allNotes) {
            for (outlink in note.outlinks) {
                val targetKey = outlink.lowercase()
                val targetNote = titleToNote[targetKey]
                if (targetNote != null) {
                    edges.add(GraphEdge(sourceId = note.title, targetId = targetNote.title))
                    degrees[note.title] = (degrees[note.title] ?: 0) + 1
                    degrees[targetNote.title] = (degrees[targetNote.title] ?: 0) + 1
                }
            }
        }

        // Generate circular initial distribution
        val count = allNotes.size
        val radius = 320f
        val nodes = allNotes.mapIndexed { index, note ->
            val angle = (index.toDouble() / count.coerceAtLeast(1)) * 2 * Math.PI
            val deg = degrees[note.title] ?: 0
            GraphNode(
                id = note.title,
                title = note.title,
                folder = note.folder,
                degree = deg,
                x = (Math.cos(angle) * (radius + (index % 3) * 60)).toFloat(),
                y = (Math.sin(angle) * (radius + (index % 3) * 60)).toFloat()
            )
        }

        return VaultGraphData(nodes, edges)
    }
}
