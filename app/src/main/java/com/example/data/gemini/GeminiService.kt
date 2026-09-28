package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
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

    val veniceSystemPrompt = """
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
        
        To run a full system health & diagnostic audit (checks broken links, orphans, disk usage):
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
        *(Available dynamic scripts: `todo_aggregator` [extracts all tasks into Master Tasks MOC], `frontmatter_injector` [adds YAML metadata to all notes], `word_frequency_analyzer` [writes Lexical Analytics MOC], `export_vault_json` [exports vault graph as JSON], `wikilink_normalizer` [cleans link whitespace], `backup_vault` [zips vault into Download/ObsidianVault_Backups], `generate_moc_index` [Map of Content], `clean_empty_files` [purges 0-byte stubs], `regex_replace` with "pattern" and "replacement")*
        
        You may invoke multiple tool calls in a single turn. You can also mix markdown explanations with tool_call blocks.
    """.trimIndent()

    suspend fun generateResponse(
        messages: List<ChatMessage>,
        userPrompt: String,
        model: GeminiModel = GeminiModel.FLASH_3_5,
        vaultNotesContext: List<VaultNote> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please add GEMINI_API_KEY in the AI Studio Secrets panel.")
            )
        }

        try {
            val root = JSONObject()

            // System Instruction
            var systemContent = veniceSystemPrompt
            if (vaultNotesContext.isNotEmpty()) {
                val vaultSummary = vaultNotesContext.take(6).joinToString("\n\n") { note ->
                    "--- Vault Note: [[${note.title}]] (Path: ${note.path}) ---\n" +
                            note.content.take(1200)
                }
                systemContent += "\n\n### Current Ingested Vault Knowledge Context:\n$vaultSummary"
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
                .put("temperature", 0.7)
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
        model: GeminiModel = GeminiModel.PRO_3_1
    ): Result<String> {
        val prompt = """
            Synthesize a comprehensive, authoritative Wiki Node for topic: "$topic".
            Follow the Venice AI objective standard. 
            Use high information density and structural headings.
            Naturally incorporate [[wikilinks]] to existing vault entities where relevant.
            Existing notes available in vault: ${relatedNotes.map { "[[${it.title}]]" }.joinToString(", ")}.
            
            Format strictly as a Markdown document starting with:
            # $topic
            
            #wiki #synthesis
        """.trimIndent()

        return generateResponse(
            messages = emptyList(),
            userPrompt = prompt,
            model = model,
            vaultNotesContext = relatedNotes
        )
    }

    suspend fun suggestLinks(
        noteTitle: String,
        noteContent: String,
        allVaultTitles: List<String>
    ): Result<String> {
        val prompt = """
            Analyze the following active note "$noteTitle" and compare against all known vault note titles:
            Known Titles: ${allVaultTitles.joinToString(", ")}
            
            Active Note Content:
            $noteContent
            
            Identify 3 to 6 high-value conceptual connections that should be linked with [[Wikilinks]].
            Provide a direct, concise bullet list showing:
            - **[[Suggested Title]]**: Reason for connection in one concise sentence.
        """.trimIndent()

        return generateResponse(
            messages = emptyList(),
            userPrompt = prompt,
            model = GeminiModel.FLASH_3_5
        )
    }
}
