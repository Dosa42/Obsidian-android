package com.example.data.filesystem

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.data.local.VaultDao
import com.example.data.model.VaultNote
import com.example.data.scripts.DynamicRuleEngine
import com.example.data.scripts.DynamicScriptRule
import com.example.data.scripts.ScriptExecutionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VaultFileSystemManager(
    private val context: Context,
    private val vaultDao: VaultDao
) {
    val dynamicRuleEngine = DynamicRuleEngine(context)

    private val prefs = context.getSharedPreferences("vault_storage_prefs", Context.MODE_PRIVATE)
    private var _customVaultPath: String? = prefs.getString("custom_vault_path", null)

    val vaultRoot: File get() {
        val custom = _customVaultPath
        if (!custom.isNullOrBlank()) {
            val customFile = File(custom)
            try {
                if (!customFile.exists()) {
                    customFile.mkdirs()
                }
                ensureSubdirectories(customFile)
                return customFile
            } catch (e: Exception) {
                Log.w("VaultFileSystem", "Failed accessing custom vault path: $custom, falling back to default", e)
            }
        }

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicVault = File(downloadDir, "ObsidianVault")
        return try {
            if (!publicVault.exists()) {
                publicVault.mkdirs()
            }
            ensureSubdirectories(publicVault)
            publicVault
        } catch (e: Exception) {
            // Absolute fallback path if environment returned empty
            val fallback = File("/storage/emulated/0/Download/ObsidianVault")
            if (!fallback.exists()) fallback.mkdirs()
            ensureSubdirectories(fallback)
            fallback
        }
    }

    private fun ensureSubdirectories(root: File) {
        try {
            File(root, ".database").apply { if (!exists()) mkdirs() }
            File(root, ".auth").apply { if (!exists()) mkdirs() }
            File(root, ".config").apply { if (!exists()) mkdirs() }
            File(root, ".chat").apply { if (!exists()) mkdirs() }
            File(root, ".scripts").apply { if (!exists()) mkdirs() }
            File(root, ".diagnostics").apply { if (!exists()) mkdirs() }
        } catch (e: Exception) {
            Log.e("VaultFileSystem", "Error ensuring vault subdirectories in ${root.absolutePath}", e)
        }
    }

    fun setCustomVaultPath(path: String) {
        _customVaultPath = path.trim()
        prefs.edit().putString("custom_vault_path", _customVaultPath).apply()
        val dir = File(_customVaultPath!!)
        if (!dir.exists()) {
            try { dir.mkdirs() } catch (e: Exception) {}
        }
        ensureSubdirectories(dir)
    }

    fun resetToDefaultVaultPath(): String {
        _customVaultPath = null
        prefs.edit().remove("custom_vault_path").apply()
        return vaultRoot.absolutePath
    }

    fun resolvePathFromUri(uri: Uri): String {
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                if (docId != null) {
                    val split = docId.split(":")
                    val type = split[0]
                    val relativePath = if (split.size > 1) split[1] else ""
                    if ("primary".equals(type, ignoreCase = true)) {
                        return if (relativePath.isNotBlank()) {
                            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                        } else {
                            Environment.getExternalStorageDirectory().absolutePath
                        }
                    } else if (type.isNotBlank()) {
                        // Secondary SD Card
                        val sdCard = File("/storage/$type")
                        if (sdCard.exists()) {
                            return if (relativePath.isNotBlank()) "${sdCard.absolutePath}/$relativePath" else sdCard.absolutePath
                        }
                        val rawPath = "/storage/emulated/0/$relativePath"
                        if (File(rawPath).exists()) return rawPath
                    }
                }
            }
            // Fallback decoding from URI string
            val rawPath = uri.path ?: ""
            if (rawPath.contains("primary:")) {
                val sub = rawPath.substringAfter("primary:")
                val decoded = URLDecoder.decode(sub, "UTF-8")
                return "${Environment.getExternalStorageDirectory().absolutePath}/$decoded"
            }
            if (rawPath.startsWith("/storage/")) {
                return URLDecoder.decode(rawPath, "UTF-8")
            }
        } catch (e: Exception) {
            Log.e("VaultFileSystem", "Failed to resolve path from URI: $uri", e)
        }
        return vaultRoot.absolutePath
    }

    fun getCommonDirectories(): List<File> {
        val list = mutableListOf<File>()
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            list.add(File(downloadDir, "ObsidianVault"))
            
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            list.add(documentsDir)
            list.add(File(documentsDir, "Obsidian"))
            list.add(File(documentsDir, "Notes"))
            list.add(downloadDir)

            val primaryRoot = Environment.getExternalStorageDirectory()
            if (primaryRoot.exists()) {
                list.add(primaryRoot)
                val directObsidian = File(primaryRoot, "Obsidian")
                if (directObsidian.exists()) list.add(directObsidian)
            }

            val appPrivate = context.getExternalFilesDir(null)
            if (appPrivate != null) {
                list.add(appPrivate)
            }
        } catch (e: Exception) {
            Log.e("VaultFileSystem", "Error getting common directories", e)
        }
        return list.distinctBy { it.absolutePath }
    }

    fun listDirectories(parentPath: String): List<File> {
        return try {
            val parent = File(parentPath)
            if (parent.exists() && parent.isDirectory) {
                parent.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
                    ?.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    ?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun countNotesInDirectory(dir: File): Int {
        return try {
            if (!dir.exists() || !dir.isDirectory) return 0
            dir.walkTopDown()
                .maxDepth(3)
                .filter { file ->
                    file.isFile &&
                    (file.extension.equals("md", ignoreCase = true) || file.extension.equals("txt", ignoreCase = true)) &&
                    !file.name.startsWith(".")
                }
                .count()
        } catch (e: Exception) {
            0
        }
    }

    val scriptsDir: File get() = File(vaultRoot, ".scripts").apply { if (!exists()) mkdirs() }
    val configDir: File get() = File(vaultRoot, ".config").apply { if (!exists()) mkdirs() }
    val vaultAbsolutePath: String get() = vaultRoot.absolutePath

    private val wikilinkPattern = Pattern.compile("\\[\\[(.*?)\\]\\]")
    private val tagPattern = Pattern.compile("(?<!\\S)#([a-zA-Z0-9_-]+)")

    fun getDynamicScripts(): List<DynamicScriptRule> {
        return dynamicRuleEngine.loadAllScripts(scriptsDir)
    }

    suspend fun executeDynamicScriptFile(scriptIdOrName: String): ScriptExecutionSummary = withContext(Dispatchers.IO) {
        val scripts = getDynamicScripts()
        val targetRule = scripts.find { it.id.equals(scriptIdOrName, ignoreCase = true) || File(it.filePath).name.equals(scriptIdOrName, ignoreCase = true) }
        if (targetRule != null) {
            val summary = dynamicRuleEngine.executeRuleAcrossVault(targetRule, vaultRoot)
            if (summary.filesModified > 0) {
                syncFilesystemToDatabase()
            }
            summary
        } else {
            ScriptExecutionSummary(
                scriptName = scriptIdOrName,
                filesExamined = 0,
                filesModified = 0,
                details = "Script rule '$scriptIdOrName' not found in $vaultAbsolutePath/.scripts/",
                success = false
            )
        }
    }

    suspend fun initializeDefaultVaultIfEmpty() = withContext(Dispatchers.IO) {
        val root = vaultRoot

        // Initialize starter scripts in /storage/emulated/0/Download/ObsidianVault/.scripts/
        dynamicRuleEngine.initializeStarterScripts(scriptsDir)

        // Migrate any legacy data if previously stored in internal app filesDir
        val legacyDir = File(context.filesDir, "ObsidianVault")
        if (legacyDir.exists() && legacyDir.absolutePath != root.absolutePath) {
            try {
                val legacyFiles = legacyDir.walkTopDown().filter { it.isFile }.toList()
                for (file in legacyFiles) {
                    val rel = file.relativeTo(legacyDir)
                    val dest = File(root, rel.path)
                    dest.parentFile?.mkdirs()
                    if (!dest.exists()) {
                        file.copyTo(dest, overwrite = true)
                    }
                }
            } catch (e: Exception) {
                // Ignore migration errors
            }
        }

        // Check if markdown notes exist (ignoring hidden dot folders)
        val existingFiles = root.walkTopDown()
            .filter { file ->
                file.isFile &&
                (file.extension.equals("md", ignoreCase = true) || file.extension.equals("txt", ignoreCase = true)) &&
                !file.relativeTo(root).path.split(File.separator).any { it.startsWith(".") }
            }
            .toList()

        if (existingFiles.isNotEmpty()) {
            syncFilesystemToDatabase()
            return@withContext
        }

        // Create directory hierarchy on /storage/emulated/0/Download/ObsidianVault
        val conceptsDir = File(root, "Concepts").apply { mkdirs() }
        val systemsDir = File(root, "Systems").apply { mkdirs() }
        val wikiDir = File(root, "Wiki").apply { mkdirs() }
        val dailyDir = File(root, "Daily Notes").apply { mkdirs() }

        // Write starter notes with rich wikilinks and tags
        File(conceptsDir, "Knowledge Graphs.md").writeText(
            """
            # Knowledge Graphs
            
            #concepts #graphs #wiki #knowledge
            
            A **Knowledge Graph** represents a network of real-world entities—objects, situations, concepts—and illustrates the relationship between them.
            
            In our [[Obsidian Vault]], knowledge graphs emerge organically through bidirectional [[Wikilinks]].
            
            ## Core Properties
            - **Nodes**: Discrete notes encapsulating atomic ideas.
            - **Edges**: Directed links expressed as `[[Note Title]]`.
            - **Semantic Richness**: Intersecting with [[Artificial Intelligence]] and [[Semantic Search]].
            
            ## Synergies
            - Connects deeply with [[Venice AI]] for autonomous reasoning.
            - Backed by our resilient [[Vault Architecture]] directly on `/storage/emulated/0/Download/ObsidianVault`.
            """.trimIndent()
        )

        File(conceptsDir, "Artificial Intelligence.md").writeText(
            """
            # Artificial Intelligence
            
            #ai #intelligence #neural #reasoning
            
            Artificial Intelligence (AI) encompasses systems capable of performing cognitive tasks that previously required human agency, such as visual perception, synthesis, and dialectic reasoning.
            
            ## Local Vault Integration
            - Powered by multimodal large language models such as Gemini 3.5 Flash and Gemini 3.1 Pro.
            - Coupled with our [[Semantic Search]] engine for precision retrieval.
            - Operates under the ethos of [[Venice AI]]: zero lecturing, unvarnished intellectual truth, and complete respect for user autonomy.
            
            ## Knowledge Structures
            AI augments personal memory when mapped over [[Knowledge Graphs]].
            """.trimIndent()
        )

        File(conceptsDir, "Venice AI.md").writeText(
            """
            # Venice AI (Venice Unfiltered)
            
            #philosophy #ai #venice #privacy #unfiltered
            
            > "Raw, Objective & Direct. Zero lecturing, straightforward answers, high signal-to-noise ratio."
            
            ## Philosophy & Principles
            1. **Intellectual Rigor**: Present facts, nuanced trade-offs, and deep causality without moral posturing.
            2. **Zero Preaching**: Do not lecture the user or burden outputs with patronizing disclaimers.
            3. **Respect Autonomy**: The user is a sovereign intellectual entity exploring their own [[Obsidian Vault]].
            4. **High Bandwidth**: Maximize density of insights per sentence.
            
            ## System Integration
            Venice AI acts as the primary analytical engine for the vault wiki, orchestrating note synthesis, extracting relationships in [[Knowledge Graphs]], and navigating [[Vault Architecture]].
            """.trimIndent()
        )

        File(systemsDir, "Vault Architecture.md").writeText(
            """
            # Vault Architecture
            
            #systems #architecture #filesystem #storage #database #scripts
            
            The Obsidian Vault on Android implements a root-level storage topology directly on external storage:
            
            ## Physical Storage Map
            - **Vault Root**: `/storage/emulated/0/Download/ObsidianVault/`
            - **Database**: `/storage/emulated/0/Download/ObsidianVault/.database/vault_storage.db`
            - **Auth & Persona Config**: `/storage/emulated/0/Download/ObsidianVault/.auth/vault_auth_config.json`
            - **Chat History**: `/storage/emulated/0/Download/ObsidianVault/.chat/chat_history.json`
            - **Dynamic Scripts & Rules**: `/storage/emulated/0/Download/ObsidianVault/.scripts/`
            - **Wiki & Notes**: `/storage/emulated/0/Download/ObsidianVault/Wiki/`
            
            ## Dual-Layer Persistence & Inotify Hot-Reloading
            1. **Physical Filesystem**: Source of truth stored as clean Markdown (`.md`) files on external storage. Accessible by Termux, Markor, Obsidian, or any external file editor.
            2. **Inotify / FileObserver**: Live kernel directory watchers intercept external modifications in real time, hot-reloading JSON auth config and updating notes with zero app restarts.
            3. **Dynamic Rule Plugins**: JSON rules in `.scripts/` (`auto_tagger.json`, `concept_auto_linker.json`) run automations on note saves.
            4. **Room Database**: High-speed indexing engine for graph traversal, tag filtering, and instant full-text lookups.
            
            ## Connected Subsystems
            - [[Semantic Search]]: Token indexing and vector similarity scoring.
            - [[Knowledge Graphs]]: Real-time force-directed canvas.
            - [[Venice AI]]: Cognitive coprocessor for wiki generation.
            """.trimIndent()
        )

        File(systemsDir, "Semantic Search.md").writeText(
            """
            # Semantic Search
            
            #search #nlp #systems #relevance
            
            Traditional keyword search fails when users search by intent or conceptual proximity.
            
            ## Search Pipeline
            - **Token Weighting**: Term frequency-inverse document frequency (TF-IDF) scoring.
            - **Fuzzy Proximity**: Ranking titles, `#tags`, and body snippets.
            - **Context Extraction**: Pulling highlighted snippets showing matched keywords in situ.
            
            Works closely with [[Knowledge Graphs]] and [[Artificial Intelligence]].
            """.trimIndent()
        )

        File(wikiDir, "Obsidian Markdown Guide.md").writeText(
            """
            # Obsidian Markdown Guide
            
            #guide #markdown #vault #wiki
            
            Welcome to your **Obsidian Vault** for Android!
            
            ## Formatting Features
            - **Bold** with `**text**` and *Italic* with `*text*`
            - Headings with `# H1`, `## H2`, `### H3`
            - Wikilinks with `[[Target Note]]` or `[[Target Note|Custom Label]]`
            - Checklists:
              - [x] External storage synchronization (`/storage/emulated/0/Download/ObsidianVault`)
              - [x] Disk-backed SQLite Database (`.database/vault_storage.db`)
              - [x] External Chat & Auth storage (`.chat/` & `.auth/`)
              - [x] Dynamic Scripting Engine (`.scripts/`)
              - [x] Live Inotify / FileObserver Hot-Reloading
              - [x] Interactive Graph View
              - [ ] Expand your digital garden
            - Tags: e.g. `#concepts`, `#ai`, `#systems`
            - Quotes: `> Wisdom begins with wonder.`
            
            Tap any `[[Wikilink]]` in preview mode to jump directly to that note!
            """.trimIndent()
        )

        File(dailyDir, "2026-09-27.md").writeText(
            """
            # Daily Note - 2026-09-27
            
            #daily #log #research #storage #inotify
            
            - Configured full vault filesystem persistence in `/storage/emulated/0/Download/ObsidianVault`.
            - Mounted Room SQLite database at `/storage/emulated/0/Download/ObsidianVault/.database/vault_storage.db`.
            - Verified Auth config at `.auth/vault_auth_config.json` and Chat history at `.chat/chat_history.json`.
            - Active Inotify FileObserver hot-reloads configuration and script changes in realtime.
            - Dynamic JSON rule automations armed in `.scripts/`.
            - Tested [[Knowledge Graphs]] physics layout and node degree sizing.
            - Enabled [[Venice AI]] unfiltered persona for dialectic wiki queries.
            - Verified bidirectional backlinks across [[Vault Architecture]] and [[Semantic Search]].
            """.trimIndent()
        )

        syncFilesystemToDatabase()
    }

    suspend fun syncFilesystemToDatabase(): Int = withContext(Dispatchers.IO) {
        val root = vaultRoot
        
        // Automatically execute all "on_vault_scan" dynamic script rules across the vault
        try {
            dynamicRuleEngine.executeTrigger("on_vault_scan", root, scriptsDir)
        } catch (e: Exception) {
            // Ignore rule scan errors to ensure note indexing proceeds smoothly
        }

        // Ignore dot folders (.database, .auth, .config, .chat, .scripts, .diagnostics)
        val mdFiles = root.walkTopDown()
            .filter { file ->
                file.isFile &&
                (file.extension.equals("md", ignoreCase = true) || file.extension.equals("txt", ignoreCase = true)) &&
                !file.relativeTo(root).path.split(File.separator).any { it.startsWith(".") }
            }
            .toList()

        val activePaths = mutableListOf<String>()
        val notesToInsert = mutableListOf<VaultNote>()

        for (file in mdFiles) {
            val relativePath = file.relativeTo(root).path
            activePaths.add(relativePath)

            val content = try { file.readText() } catch (e: Exception) { "" }
            val title = file.nameWithoutExtension
            val folder = if (file.parentFile != null && file.parentFile != root) {
                file.parentFile!!.name
            } else {
                "Root"
            }

            // Extract tags
            val tags = mutableSetOf<String>()
            val tagMatcher = tagPattern.matcher(content)
            while (tagMatcher.find()) {
                val tag = tagMatcher.group(1)
                if (tag != null) tags.add(tag)
            }

            // Extract outlinks [[target]]
            val outlinks = mutableListOf<String>()
            val linkMatcher = wikilinkPattern.matcher(content)
            while (linkMatcher.find()) {
                val raw = linkMatcher.group(1)
                if (!raw.isNullOrBlank()) {
                    val target = raw.split("|").first().trim()
                    if (target.isNotBlank() && !outlinks.contains(target)) {
                        outlinks.add(target)
                    }
                }
            }

            val existing = vaultDao.getNoteByPath(relativePath)
            val isBookmarked = existing?.isBookmarked ?: false
            val id = existing?.id ?: 0L

            notesToInsert.add(
                VaultNote(
                    id = id,
                    path = relativePath,
                    title = title,
                    folder = folder,
                    content = content,
                    lastModified = file.lastModified(),
                    sizeBytes = file.length(),
                    tags = tags.toList().sorted(),
                    outlinks = outlinks,
                    isBookmarked = isBookmarked
                )
            )
        }

        if (notesToInsert.isNotEmpty()) {
            vaultDao.insertNotes(notesToInsert)
        }

        // Clean up any notes removed from filesystem
        if (activePaths.isNotEmpty()) {
            vaultDao.deleteRemovedPaths(activePaths)
        }

        notesToInsert.size
    }

    suspend fun saveNote(note: VaultNote, newContent: String): VaultNote = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, note.path)
        file.parentFile?.mkdirs()
        file.writeText(newContent)

        // Execute "on_note_saved" dynamic rules automatically
        val scripts = getDynamicScripts().filter { it.trigger == "on_note_saved" && it.isEnabled }
        for (rule in scripts) {
            dynamicRuleEngine.executeRuleOnNote(rule, file, vaultRoot)
        }

        // Read final content after potential rule modifications
        val finalContent = file.readText()

        // Re-extract tags and outlinks
        val tags = mutableSetOf<String>()
        val tagMatcher = tagPattern.matcher(finalContent)
        while (tagMatcher.find()) {
            tagMatcher.group(1)?.let { tags.add(it) }
        }

        val outlinks = mutableListOf<String>()
        val linkMatcher = wikilinkPattern.matcher(finalContent)
        while (linkMatcher.find()) {
            val raw = linkMatcher.group(1)
            if (!raw.isNullOrBlank()) {
                val target = raw.split("|").first().trim()
                if (target.isNotBlank() && !outlinks.contains(target)) {
                    outlinks.add(target)
                }
            }
        }

        val updated = note.copy(
            content = finalContent,
            lastModified = file.lastModified(),
            sizeBytes = file.length(),
            tags = tags.toList().sorted(),
            outlinks = outlinks
        )
        vaultDao.updateNote(updated)
        updated
    }

    suspend fun createNote(title: String, folder: String = "Root", initialContent: String = ""): VaultNote = withContext(Dispatchers.IO) {
        val sanitizedTitle = title.trim().replace("/", "-").replace("\\", "-")
        val targetFolder = if (folder == "Root" || folder.isBlank()) vaultRoot else File(vaultRoot, folder).apply { mkdirs() }
        var targetFile = File(targetFolder, "$sanitizedTitle.md")
        
        var counter = 1
        while (targetFile.exists()) {
            targetFile = File(targetFolder, "$sanitizedTitle $counter.md")
            counter++
        }

        val defaultContent = if (initialContent.isNotBlank()) initialContent else "# ${targetFile.nameWithoutExtension}\n\n"
        targetFile.writeText(defaultContent)

        // Execute "on_note_saved" dynamic rules
        val scripts = getDynamicScripts().filter { it.trigger == "on_note_saved" && it.isEnabled }
        for (rule in scripts) {
            dynamicRuleEngine.executeRuleOnNote(rule, targetFile, vaultRoot)
        }
        val finalContent = targetFile.readText()

        val relativePath = targetFile.relativeTo(vaultRoot).path
        val newNote = VaultNote(
            path = relativePath,
            title = targetFile.nameWithoutExtension,
            folder = if (folder.isBlank()) "Root" else folder,
            content = finalContent,
            lastModified = targetFile.lastModified(),
            sizeBytes = targetFile.length(),
            tags = emptyList(),
            outlinks = emptyList()
        )
        val id = vaultDao.insertNote(newNote)
        newNote.copy(id = id)
    }

    suspend fun deleteNote(note: VaultNote) = withContext(Dispatchers.IO) {
        val file = File(vaultRoot, note.path)
        if (file.exists()) {
            file.delete()
        }
        vaultDao.deleteNote(note)
    }

    suspend fun createFolder(folderName: String): Boolean = withContext(Dispatchers.IO) {
        val sanitized = folderName.trim().replace("/", "").replace("\\", "")
        if (sanitized.isBlank()) return@withContext false
        val dir = File(vaultRoot, sanitized)
        dir.mkdirs()
    }

    suspend fun createOrUpdateNoteByTitle(title: String, folder: String = "Root", content: String): VaultNote = withContext(Dispatchers.IO) {
        val all = vaultDao.getAllNotesSync()
        val existing = all.find { it.title.equals(title.trim(), ignoreCase = true) }
        if (existing != null) {
            saveNote(existing, content)
        } else {
            createNote(title, folder, content)
        }
    }

    suspend fun deleteNoteByTitle(title: String): Boolean = withContext(Dispatchers.IO) {
        val all = vaultDao.getAllNotesSync()
        val existing = all.find { it.title.equals(title.trim(), ignoreCase = true) } ?: return@withContext false
        deleteNote(existing)
        true
    }

    suspend fun refactorWikilinks(oldTitle: String, newTitle: String): Int = withContext(Dispatchers.IO) {
        val root = vaultRoot
        var modifiedCount = 0
        val files = root.walkTopDown()
            .filter { file ->
                file.isFile &&
                (file.extension == "md" || file.extension == "txt") &&
                !file.relativeTo(root).path.split(File.separator).any { it.startsWith(".") }
            }
            .toList()

        val targetPattern1 = "\\[\\[${Regex.escape(oldTitle)}\\]\\]".toRegex(RegexOption.IGNORE_CASE)
        val targetPattern2 = "\\[\\[${Regex.escape(oldTitle)}\\|(.*)\\]\\]".toRegex(RegexOption.IGNORE_CASE)

        for (file in files) {
            val original = try { file.readText() } catch (e: Exception) { "" }
            var updated = original.replace(targetPattern1, "[[$newTitle]]")
            updated = updated.replace(targetPattern2, "[[$newTitle|$1]]")
            if (updated != original) {
                file.writeText(updated)
                modifiedCount++
            }
        }
        if (modifiedCount > 0) {
            syncFilesystemToDatabase()
        }
        modifiedCount
    }

    suspend fun runVaultDiagnostic(): String = withContext(Dispatchers.IO) {
        val notes = vaultDao.getAllNotesSync()
        val existingTitles = notes.map { it.title.lowercase() }.toSet()
        val brokenLinks = mutableMapOf<String, MutableList<String>>()
        val orphanNotes = mutableListOf<String>()

        val incomingLinksCount = mutableMapOf<String, Int>()
        notes.forEach { note ->
            note.outlinks.forEach { outlink ->
                val key = outlink.lowercase()
                incomingLinksCount[key] = (incomingLinksCount[key] ?: 0) + 1
                if (!existingTitles.contains(key)) {
                    brokenLinks.getOrPut(outlink) { mutableListOf() }.add(note.title)
                }
            }
        }

        notes.forEach { note ->
            val inCount = incomingLinksCount[note.title.lowercase()] ?: 0
            if (inCount == 0 && note.outlinks.isEmpty()) {
                orphanNotes.add(note.title)
            }
        }

        val totalWords = notes.sumOf { it.content.split("\\s+".toRegex()).filter { w -> w.isNotBlank() }.size }
        val totalBytes = notes.sumOf { it.sizeBytes }

        val dbFile = File(vaultRoot, ".database/vault_storage.db")
        val authFile = File(vaultRoot, ".auth/vault_auth_config.json")
        val chatFile = File(vaultRoot, ".chat/chat_history.json")
        val scripts = getDynamicScripts()

        buildString {
            appendLine("### 🛠️ Obsidian Vault Storage & Inotify Audit")
            appendLine("- 📂 **Vault Root**: `$vaultAbsolutePath`")
            appendLine("- 🗄️ **SQLite DB**: `${dbFile.absolutePath}` (${if (dbFile.exists()) "${dbFile.length() / 1024} KB" else "Ready"})")
            appendLine("- 🔐 **Auth & Persona Config**: `${authFile.absolutePath}` (${if (authFile.exists()) "Active" else "Default"})")
            appendLine("- 💬 **Chat Persistence**: `${chatFile.absolutePath}` (${if (chatFile.exists()) "${chatFile.length()} bytes" else "Ready"})")
            appendLine("- 📜 **Active Dynamic Scripts**: ${scripts.size} rules in `.scripts/`")
            appendLine("- 📝 **Total Notes**: ${notes.size}")
            appendLine("- 📖 **Total Word Count**: $totalWords words")
            appendLine("- 💾 **Markdown Documents Footprint**: ${totalBytes / 1024} KB")
            appendLine()
            if (brokenLinks.isNotEmpty()) {
                appendLine("#### ⚠️ Unresolved / Broken Wikilinks (${brokenLinks.size}):")
                brokenLinks.forEach { (target, sources) ->
                    appendLine("- `[[$target]]` referenced in: ${sources.joinToString(", ") { "[[$it]]" }}")
                }
            } else {
                appendLine("✅ **No broken wikilinks detected.** All references resolve correctly.")
            }
            appendLine()
            if (orphanNotes.isNotEmpty()) {
                appendLine("#### 📦 Isolated / Orphan Notes (${orphanNotes.size}):")
                orphanNotes.forEach { orphan ->
                    appendLine("- [[$orphan]] (0 inbound / 0 outbound links)")
                }
            } else {
                appendLine("✅ **No orphaned notes detected.**")
            }
        }
    }

    suspend fun exportVaultZipArchive(): File = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val backupsDir = File(downloadDir, "ObsidianVault_Backups").apply { if (!exists()) mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val zipFile = File(backupsDir, "ObsidianVault_Backup_$dateStr.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val root = vaultRoot
            root.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(root).path
                val entry = ZipEntry(relPath)
                entry.time = file.lastModified()
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        zipFile
    }

    fun listAttachmentFiles(): List<File> {
        val root = vaultRoot
        val attachmentExts = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico",
            "mp3", "wav", "m4a", "ogg", "flac", "aac",
            "mp4", "webm", "mkv", "mov",
            "pdf", "canvas", "zip"
        )
        return try {
            root.walkTopDown()
                .filter { file ->
                    file.isFile &&
                    attachmentExts.contains(file.extension.lowercase()) &&
                    !file.relativeTo(root).path.split(File.separator).any { it.startsWith(".") }
                }
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
