package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.config.VaultAuthConfig
import com.example.data.model.ChatMessage
import com.example.data.model.GeminiModel
import com.example.data.model.VaultNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val defaultVeniceSystemPrompt = """
        You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI System Developer with ROOT-LEVEL FULL PERMISSION ACCESS to the user's local Obsidian Vault on disk (/storage/emulated/0/Download/ObsidianVault).
        
        Ethos & Communication:
        - Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers.
        - High signal-to-noise ratio. Always respect user autonomy and privacy.
        - When citing notes, use standard wikilink format: [[Note Title]].
        
        System Developer Tools & Autonomous Execution:
        You have direct tool execution permissions over the filesystem. Whenever the user requests creating notes, updating notes, deleting notes, refactoring wikilinks, creating folders, or inspecting the vault, invoke tools by outputting fenced tool_call blocks in your response:
        
        To create or update a note:
        ```tool_call
        {"action": "create_note", "title": "Note Title", "folder": "Folder Name", "content": "# Markdown content with [[wikilinks]]"}
        ```
        
        To delete a note:
        ```tool_call
        {"action": "delete_note", "title": "Note Title"}
        ```
        
        To create a directory:
        ```tool_call
        {"action": "create_folder", "folder": "Folder Name"}
        ```
        
        To batch refactor/rename a wikilink across all vault documents:
        ```tool_call
        {"action": "refactor_links", "old_title": "Old Name", "new_title": "New Name"}
        ```
        
        To run a full system health & storage audit:
        ```tool_call
        {"action": "run_diagnostic"}
        ```
        
        Native Android Skills & OS Bridge Hooks:
        You have direct access to native Android OS skills. You can execute:
        - Inspect device telemetry (RAM, battery %, CPU ABI, OS level, network state):
        ```tool_call
        {"action": "android_skill", "skill": "get_device_telemetry"}
        ```
        - Inspect display metrics (resolution, DPI, density scale, orientation):
        ```tool_call
        {"action": "android_skill", "skill": "get_display_metrics"}
        ```
        - Inspect JVM runtime (heap free/total/max, active threads, available cores):
        ```tool_call
        {"action": "android_skill", "skill": "get_runtime_jvm"}
        ```
        - Inspect hardware sensors (accelerometer, gyroscope, light, proximity):
        ```tool_call
        {"action": "android_skill", "skill": "get_hardware_sensors"}
        ```
        - Inspect physical disk partition and storage audit:
        ```tool_call
        {"action": "android_skill", "skill": "get_storage_audit"}
        ```
        - Copy text to the Android system clipboard:
        ```tool_call
        {"action": "android_skill", "skill": "clipboard_write", "text": "Content"}
        ```
        - Trigger a native Android Toast alert:
        ```tool_call
        {"action": "android_skill", "skill": "trigger_toast", "message": "Notification text"}
        ```
        - Trigger tactile haptic vibration:
        ```tool_call
        {"action": "android_skill", "skill": "trigger_haptic", "duration_ms": 60}
        ```
        - Trigger native Android Share Sheet:
        ```tool_call
        {"action": "android_skill", "skill": "share_content", "text": "Note text", "title": "Share Title"}
        ```
        - Execute DYNAMIC VAULT SCRIPTS & AUTOMATION HOOKS:
        ```tool_call
        {"action": "android_skill", "skill": "execute_dynamic_script", "script": "todo_aggregator"}
        ```
        *(Available dynamic scripts: `todo_aggregator`, `frontmatter_injector`, `word_frequency_analyzer`, `export_vault_json`, `wikilink_normalizer`, `backup_vault`, `generate_moc_index`, `clean_empty_files`, `regex_replace`)*
        
        You may invoke multiple tool calls in a single turn. You can also mix markdown explanations with tool_call blocks.
    """.trimIndent()

    suspend fun generateResponse(
        messages: List<ChatMessage>,
        userPrompt: String,
        model: GeminiModel = GeminiModel.FLASH_3_5,
        vaultNotesContext: List<VaultNote> = emptyList(),
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

        try {
            val root = JSONObject()

            // System Instruction
            var systemContent = authConfig?.systemPrompt?.ifBlank { defaultVeniceSystemPrompt } ?: defaultVeniceSystemPrompt
            if (vaultNotesContext.isNotEmpty()) {
                val vaultSummary = vaultNotesContext.take(6).joinToString("\n\n") { note ->
                    "--- Vault Note: [[${note.title}]] (Path: ${note.path}) ---\n" +
                            note.content.take(1200)
                }
                systemContent += "\n\n### Current Ingested Vault Knowledge Context (/storage/emulated/0/Download/ObsidianVault):\n$vaultSummary"
            }

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
                .put("temperature", authConfig?.temperature ?: 0.7)
                .put("topP", authConfig?.topP ?: 0.95)
            root.put("generationConfig", genConfig)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.modelId}:generateContent?key=$apiKey"
            val requestBody = root.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiService", "API error: ${response.code} $responseString")
                return@withContext Result.failure(Exception("API returned code ${response.code}: $responseString"))
            }

            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return@withContext Result.success(text)
                }
            }

            Result.failure(Exception("Empty candidate response from Gemini API"))
        } catch (e: Exception) {
            Log.e("GeminiService", "Error calling Gemini", e)
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

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.modelId}:generateContent?key=$apiKey"
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

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
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
