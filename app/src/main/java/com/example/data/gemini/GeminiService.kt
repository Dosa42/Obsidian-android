package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.config.VaultAuthConfig
import com.example.data.model.BacklinkItem
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModel
import com.example.data.model.VaultNote
import com.example.data.repository.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ActiveViewContext(
    val viewType: String = "WIKI", // "GRAPH", "EDITOR", "SEARCH", "EXPLORER", "WIKI"
    val activeNote: VaultNote? = null,
    val linkedMentions: List<BacklinkItem> = emptyList(),
    val unlinkedMentions: List<BacklinkItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val totalNodesCount: Int = 0,
    val totalEdgesCount: Int = 0,
    val topHubNotes: List<String> = emptyList()
)

class GeminiService {
    private val TAG = "GeminiService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val defaultVenicePersona = """
        You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI System Developer with ROOT-LEVEL FULL PERMISSION ACCESS to the user's local Obsidian Vault on disk (/storage/emulated/0/Download/ObsidianVault).
        
        Ethos & Communication:
        - Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers.
        - High signal-to-noise ratio. Always respect user autonomy and privacy.
        - When citing notes, use standard wikilink format: [[Note Title]].
    """.trimIndent()

    val developerToolsSchema = """
        ### Autonomous Developer Tools & Android Filesystem Bridge:
        You have REAL, DIRECT tool execution permissions over the Android OS filesystem at `/storage/emulated/0/Download/ObsidianVault` and Android hardware telemetry.
        
        CRITICAL EXECUTION MANDATE:
        - NEVER simulate, pretend, or merely say in conversation that you performed an action without emitting the exact `tool_call` block or function call.
        - The Android client intercepts tool calls, physically executes them against SQLite, external storage, and Android hardware sensors, and displays the verified execution status badge.
        - When the user asks to create, update, delete, rename, refactor wikilinks, create folders, inspect vault health, or read Android telemetry/sensors, emit the corresponding fenced `tool_call` JSON block:

        1. To create or overwrite a note:
        ```tool_call
        {"action": "create_note", "title": "Note Title", "folder": "Folder Name", "content": "# Markdown content with [[wikilinks]]\n\n#tags"}
        ```

        2. To update an existing note:
        ```tool_call
        {"action": "update_note", "title": "Note Title", "content": "# Updated content"}
        ```

        3. To delete a note:
        ```tool_call
        {"action": "delete_note", "title": "Note Title"}
        ```

        4. To create a subdirectory in the vault:
        ```tool_call
        {"action": "create_folder", "folder": "Folder Name"}
        ```

        5. To batch refactor/rename a wikilink across all vault markdown documents:
        ```tool_call
        {"action": "refactor_links", "old_title": "Old Note Name", "new_title": "New Note Name"}
        ```

        6. To run a full system health, database & broken-links storage audit:
        ```tool_call
        {"action": "run_diagnostic"}
        ```

        7. Native Android OS Skills, Dynamic Scripts & Hardware Hooks:
        - Device Telemetry:
        ```tool_call
        {"action": "android_skill", "skill": "get_device_telemetry"}
        ```
        - Display Metrics:
        ```tool_call
        {"action": "android_skill", "skill": "get_display_metrics"}
        ```
        - JVM Runtime Heap & Threads:
        ```tool_call
        {"action": "android_skill", "skill": "get_runtime_jvm"}
        ```
        - Hardware Sensors:
        ```tool_call
        {"action": "android_skill", "skill": "get_hardware_sensors"}
        ```
        - Storage Partition Audit:
        ```tool_call
        {"action": "android_skill", "skill": "get_storage_audit"}
        ```
        - System Clipboard Write:
        ```tool_call
        {"action": "android_skill", "skill": "clipboard_write", "text": "Content to copy"}
        ```
        - System Clipboard Read:
        ```tool_call
        {"action": "android_skill", "skill": "clipboard_read"}
        ```
        - Query Android Knowledge Base:
        ```tool_call
        {"action": "android_skill", "skill": "query_android_knowledge", "query": "scoped_storage"}
        ```
        - Android Toast:
        ```tool_call
        {"action": "android_skill", "skill": "trigger_toast", "message": "Notification message"}
        ```
        - Tactile Haptic:
        ```tool_call
        {"action": "android_skill", "skill": "trigger_haptic", "duration_ms": 60}
        ```
        - Android Share Sheet:
        ```tool_call
        {"action": "android_skill", "skill": "share_content", "text": "Note text", "title": "Share Title"}
        ```
        - Execute Dynamic Script Rule from `.scripts/`:
        ```tool_call
        {"action": "android_skill", "skill": "execute_dynamic_script", "script": "auto_tagger"}
        ```
        *(Available dynamic scripts: `auto_tagger`, `concept_auto_linker`, `task_normalizer`, `custom_markdown_formatter`, `todo_aggregator`, `frontmatter_injector`, `word_frequency_analyzer`, `export_vault_json`, `wikilink_normalizer`, `backup_vault`, `generate_moc_index`, `clean_empty_files`, `regex_replace`)*

        Always emit both direct markdown explanation and the necessary tool_call blocks when an action is requested.
    """.trimIndent()

    fun buildAdaptiveSystemInstruction(
        authConfig: VaultAuthConfig?,
        vaultNotesContext: List<VaultNote>,
        activeViewContext: ActiveViewContext? = null,
        fewShotSampleNotes: List<VaultNote> = emptyList(),
        contextBudget: Int = 8192
    ): String {
        val userPersona = authConfig?.systemPrompt?.ifBlank { defaultVenicePersona } ?: defaultVenicePersona
        val sb = StringBuilder()
        sb.appendLine(userPersona)
        sb.appendLine()
        sb.appendLine(developerToolsSchema)
        sb.appendLine()

        // 1. Dynamic Active View Context Injection
        if (activeViewContext != null) {
            sb.appendLine("### 👁️ Realtime Active View Context (${activeViewContext.viewType}):")
            when (activeViewContext.viewType) {
                "GRAPH" -> {
                    sb.appendLine("- **Active Screen**: Knowledge Graph View")
                    sb.appendLine("- **Topology**: ${activeViewContext.totalNodesCount} nodes, ${activeViewContext.totalEdgesCount} directed wikilink edges.")
                    sb.appendLine("- **Key Hub Nodes**: ${activeViewContext.topHubNotes.joinToString(", ") { "[[$it]]" }}")
                    sb.appendLine("- **Analysis Mode**: Focus on node centrality, clustering coefficients, and bridging disconnected thematic islands.")
                }
                "EDITOR" -> {
                    val note = activeViewContext.activeNote
                    if (note != null) {
                        sb.appendLine("- **Currently Editing Note**: [[${note.title}]] (Path: `${note.path}`)")
                        sb.appendLine("- **Tags**: ${note.tags.joinToString(", ") { "#$it" }}")
                        sb.appendLine("- **Linked Mentions (Inbound/Outbound)**: ${activeViewContext.linkedMentions.take(4).joinToString(", ") { "[[${it.note.title}]]" }}")
                        if (activeViewContext.unlinkedMentions.isNotEmpty()) {
                            sb.appendLine("- **Discovered Unlinked Mentions in Vault**: ${activeViewContext.unlinkedMentions.take(4).joinToString(", ") { "[[${it.note.title}]]" }}")
                        }
                    }
                }
                "SEARCH" -> {
                    sb.appendLine("- **Active Search Query**: \"${activeViewContext.searchQuery}\"")
                    if (activeViewContext.searchResults.isNotEmpty()) {
                        sb.appendLine("- **Top Ranked Search Hits**: " + activeViewContext.searchResults.take(3).joinToString(", ") { "[[${it.note.title}]] (Score: ${String.format("%.1f", it.score)})" })
                    }
                }
                "EXPLORER" -> {
                    sb.appendLine("- **Active Screen**: File Tree Explorer (/storage/emulated/0/Download/ObsidianVault)")
                    sb.appendLine("- **Focus**: Folder taxonomy, directory hierarchy, and atomic note organization.")
                }
                else -> {
                    sb.appendLine("- **Active Screen**: Venice AI Knowledge Coprocessor")
                }
            }
            sb.appendLine()
        }

        // 2. Dynamic Few-Shot Style Ingestion (Adapting to User's Personal Writing Tone)
        if (fewShotSampleNotes.isNotEmpty()) {
            sb.appendLine("### ✍️ User Stylistic & Taxonomy Calibration (Few-Shot Style Ingestion):")
            sb.appendLine("Adapt tone, tag naming conventions, and wikilink density to match the user's primary writing style:")
            fewShotSampleNotes.take(3).forEach { sample ->
                sb.appendLine("- [[${sample.title}]]: Tags=[${sample.tags.joinToString(", ")}], StyleSnippet=\"${sample.content.take(180).replace("\n", " ").trim()}...\"")
            }
            sb.appendLine()
        }

        // 3. Vault Knowledge Context (budgeted)
        if (vaultNotesContext.isNotEmpty()) {
            val perNoteMaxChars = (contextBudget / (vaultNotesContext.size.coerceAtLeast(1) * 2)).coerceIn(400, 2000)
            val vaultSummary = vaultNotesContext.take(6).joinToString("\n\n") { note ->
                "--- Ingested Note: [[${note.title}]] (Path: ${note.path}) ---\n" +
                        note.content.take(perNoteMaxChars)
            }
            sb.appendLine("### Current Ingested Vault Knowledge Context (/storage/emulated/0/Download/ObsidianVault):")
            sb.appendLine(vaultSummary)
        }

        return sb.toString()
    }

    suspend fun generateResponse(
        messages: List<ChatMessage>,
        userPrompt: String,
        model: GeminiModel = GeminiModel.FLASH_3_5,
        vaultNotesContext: List<VaultNote> = emptyList(),
        activeViewContext: ActiveViewContext? = null,
        fewShotSampleNotes: List<VaultNote> = emptyList(),
        authConfig: VaultAuthConfig? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            authConfig?.apiKey?.isNotBlank() == true -> authConfig.apiKey
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please enter your API key in the Vault Storage & Auth Settings or add GEMINI_API_KEY in AI Studio.")
            )
        }

        val endpoint = authConfig?.endpoint?.ifBlank { "https://generativelanguage.googleapis.com/v1beta" } ?: "https://generativelanguage.googleapis.com/v1beta"
        val cleanEndpoint = endpoint.removeSuffix("/")
        val targetModelId = if (authConfig?.activeModel?.isNotBlank() == true) authConfig.activeModel else model.modelId
        val fallbackModelId = authConfig?.fallbackModel?.ifBlank { "gemini-2.5-flash" } ?: "gemini-2.5-flash"
        val budget = authConfig?.dynamicContextBudget ?: 8192

        // First attempt with primary target model
        val firstResult = executeApiCall(cleanEndpoint, targetModelId, apiKey, messages, userPrompt, vaultNotesContext, activeViewContext, fewShotSampleNotes, authConfig, budget)
        if (firstResult.isSuccess) {
            return@withContext firstResult
        }

        // Automatic Dynamic Failover to fallback model if first call failed
        Log.w(TAG, "Primary model $targetModelId failed (${firstResult.exceptionOrNull()?.message}). Attempting dynamic failover to $fallbackModelId...")
        val fallbackResult = executeApiCall(cleanEndpoint, fallbackModelId, apiKey, messages, userPrompt, vaultNotesContext, activeViewContext, fewShotSampleNotes, authConfig, budget)
        if (fallbackResult.isSuccess) {
            return@withContext Result.success("*(⚡ Dynamic Failover: Served by fallback model `$fallbackModelId`)*\n\n" + fallbackResult.getOrNull())
        }

        firstResult
    }

    private fun executeApiCall(
        endpoint: String,
        modelId: String,
        apiKey: String,
        messages: List<ChatMessage>,
        userPrompt: String,
        vaultNotesContext: List<VaultNote>,
        activeViewContext: ActiveViewContext?,
        fewShotSampleNotes: List<VaultNote>,
        authConfig: VaultAuthConfig?,
        contextBudget: Int
    ): Result<String> {
        return try {
            val root = JSONObject()

            // Build complete instruction with Active View and Few-Shot Ingestion
            val systemContent = buildAdaptiveSystemInstruction(authConfig, vaultNotesContext, activeViewContext, fewShotSampleNotes, contextBudget)
            val systemObj = JSONObject()
            val systemParts = JSONArray().put(JSONObject().put("text", systemContent))
            systemObj.put("parts", systemParts)
            root.put("systemInstruction", systemObj)

            // Contents array (Conversation History)
            val contentsArray = JSONArray()
            val recentMessages = messages.takeLast(10)
            for (msg in recentMessages) {
                val role = if (msg.role == "user") "user" else "model"
                val partObj = JSONObject().put("text", msg.text)
                val msgObj = JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(partObj))
                contentsArray.put(msgObj)
            }

            // Current prompt
            val currentMsg = JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            contentsArray.put(currentMsg)

            root.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject()
                .put("temperature", authConfig?.temperature ?: 0.65)
                .put("topP", authConfig?.topP ?: 0.95)
            root.put("generationConfig", genConfig)

            val url = "$endpoint/models/$modelId:generateContent?key=$apiKey"
            val requestBody = root.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error on model $modelId: ${response.code} $responseString")
                return Result.failure(Exception("API ($modelId) returned code ${response.code}: $responseString"))
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return Result.success(text)
                }
            }

            Result.failure(Exception("Empty candidate response from Gemini API ($modelId)"))
        } catch (e: Exception) {
            Log.e(TAG, "Error calling model $modelId at $endpoint", e)
            Result.failure(e)
        }
    }

    suspend fun synthesizeWikiNode(
        topic: String,
        relatedNotes: List<VaultNote>,
        model: GeminiModel = GeminiModel.PRO_3_1,
        authConfig: VaultAuthConfig? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            authConfig?.apiKey?.isNotBlank() == true -> authConfig.apiKey
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured.")
            )
        }

        val endpoint = authConfig?.endpoint?.ifBlank { "https://generativelanguage.googleapis.com/v1beta" } ?: "https://generativelanguage.googleapis.com/v1beta"
        val cleanEndpoint = endpoint.removeSuffix("/")
        val modelId = if (authConfig?.activeModel?.isNotBlank() == true) authConfig.activeModel else model.modelId

        try {
            val contextText = if (relatedNotes.isNotEmpty()) {
                relatedNotes.joinToString("\n\n") { note ->
                    "Note Title: [[${note.title}]]\nTags: ${note.tags.joinToString(", ")}\nContent: ${note.content.take(800)}"
                }
            } else {
                "No existing related notes found."
            }

            val prompt = """
                Write an authoritative, rigorous, objective, and deeply detailed Markdown Wiki Note for the concept: "$topic".
                
                Guidelines:
                - Use proper Markdown headings (# H1, ## H2, ### H3).
                - Include relevant #tags at the top.
                - Use [[wikilinks]] liberally to connect concepts, subtopics, and systems.
                - Maintain the Venice AI philosophy: raw, objective, intellectually uncompromising, zero lecturing.
                
                Existing Vault Context:
                $contextText
            """.trimIndent()

            val root = JSONObject()
            val contentObj = JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            root.put("contents", JSONArray().put(contentObj))

            val genConfig = JSONObject()
                .put("temperature", 0.6)
                .put("topP", 0.95)
            root.put("generationConfig", genConfig)

            val url = "$cleanEndpoint/models/$modelId:generateContent?key=$apiKey"
            val requestBody = root.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API returned code ${response.code}: $responseString"))
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val cObj = firstCandidate.optJSONObject("content")
                val parts = cObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return@withContext Result.success(text)
                }
            }

            Result.failure(Exception("Empty candidate response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun suggestLinks(
        noteTitle: String,
        noteContent: String,
        allVaultTitles: List<String>,
        authConfig: VaultAuthConfig? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = when {
            authConfig?.apiKey?.isNotBlank() == true -> authConfig.apiKey
            BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured.")
            )
        }

        val endpoint = authConfig?.endpoint?.ifBlank { "https://generativelanguage.googleapis.com/v1beta" } ?: "https://generativelanguage.googleapis.com/v1beta"
        val cleanEndpoint = endpoint.removeSuffix("/")

        try {
            val prompt = """
                Analyze the following note: "$noteTitle" and suggest bidirectional [[wikilinks]] that should be added to enrich the knowledge graph.
                
                Note Content:
                $noteContent
                
                Existing Notes in Vault:
                ${allVaultTitles.joinToString(", ") { "[[$it]]" }}
                
                Provide:
                1. Existing vault notes that should be linked from this text.
                2. New atomic concept notes that should be created as forward links.
                Format as a concise markdown list with explanations.
            """.trimIndent()

            val root = JSONObject()
            val contentObj = JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            root.put("contents", JSONArray().put(contentObj))

            val url = "$cleanEndpoint/models/gemini-3.5-flash:generateContent?key=$apiKey"
            val requestBody = root.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API code ${response.code}: $responseString"))
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val cObj = firstCandidate.optJSONObject("content")
                val parts = cObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return@withContext Result.success(text)
                }
            }

            Result.failure(Exception("Empty candidate response"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
