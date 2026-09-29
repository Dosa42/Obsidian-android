package com.example.data.chat

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class VaultChatStorageManager(
    private val context: Context,
    private val vaultRootProvider: () -> File = {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, "ObsidianVault")
    }
) {
    private val TAG = "VaultChatStorageManager"

    private val chatDir: File get() {
        val root = try { vaultRootProvider() } catch (e: Exception) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadDir, "ObsidianVault")
        }
        val dir = File(root, ".chat")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    val chatFile: File get() = File(chatDir, "chat_history.json")
    val chatAbsolutePath: String get() = chatFile.absolutePath

    suspend fun loadChatHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val file = chatFile
        if (!file.exists() || file.length() == 0L) {
            return@withContext defaultInitialMessage()
        }

        try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val messages = mutableListOf<ChatMessage>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val role = obj.optString("role", "model")
                val text = obj.optString("text", "")
                val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                val isSynthesizing = obj.optBoolean("isSynthesizing", false)

                val citedArray = obj.optJSONArray("citedNotes")
                val citedNotes = mutableListOf<String>()
                if (citedArray != null) {
                    for (j in 0 until citedArray.length()) {
                        citedNotes.add(citedArray.getString(j))
                    }
                }

                val toolsArray = obj.optJSONArray("executedTools")
                val tools = mutableListOf<ToolExecutionResult>()
                if (toolsArray != null) {
                    for (k in 0 until toolsArray.length()) {
                        val toolObj = toolsArray.getJSONObject(k)
                        tools.add(
                            ToolExecutionResult(
                                action = toolObj.optString("action", ""),
                                target = toolObj.optString("target", ""),
                                details = toolObj.optString("details", ""),
                                success = toolObj.optBoolean("success", true)
                            )
                        )
                    }
                }

                messages.add(
                    ChatMessage(
                        id = id,
                        role = role,
                        text = text,
                        timestamp = timestamp,
                        citedNotes = citedNotes,
                        isSynthesizing = isSynthesizing,
                        executedTools = tools
                    )
                )
            }

            if (messages.isEmpty()) {
                defaultInitialMessage()
            } else {
                messages
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading chat history from ${file.absolutePath}", e)
            defaultInitialMessage()
        }
    }

    suspend fun saveChatHistory(messages: List<ChatMessage>): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = chatFile
            file.parentFile?.mkdirs()

            val array = JSONArray()
            for (msg in messages) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("isSynthesizing", msg.isSynthesizing)

                    val citedArray = JSONArray()
                    msg.citedNotes.forEach { citedArray.put(it) }
                    put("citedNotes", citedArray)

                    val toolsArray = JSONArray()
                    msg.executedTools.forEach { tool ->
                        val tObj = JSONObject().apply {
                            put("action", tool.action)
                            put("target", tool.target)
                            put("details", tool.details)
                            put("success", tool.success)
                        }
                        toolsArray.put(tObj)
                    }
                    put("executedTools", toolsArray)
                }
                array.put(obj)
            }

            file.writeText(array.toString(2))
            Log.d(TAG, "Persisted ${messages.size} chat messages to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving chat history to ${chatFile.absolutePath}", e)
            false
        }
    }

    suspend fun clearChatHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val initial = defaultInitialMessage()
        saveChatHistory(initial)
        initial
    }

    suspend fun exportChatToMarkdownFile(messages: List<ChatMessage>): File = withContext(Dispatchers.IO) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vaultDir = File(downloadDir, "ObsidianVault")
        val wikiDir = File(vaultDir, "Wiki").apply { if (!exists()) mkdirs() }
        
        val dateStr = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.getDefault()).format(Date())
        val exportFile = File(wikiDir, "Chat Export $dateStr.md")

        val sb = StringBuilder()
        sb.appendLine("# Venice AI Chat Transcript — $dateStr")
        sb.appendLine()
        sb.appendLine("#chat #venice #transcript #archive")
        sb.appendLine()
        sb.appendLine("> Saved from `/storage/emulated/0/Download/ObsidianVault/.chat/chat_history.json`")
        sb.appendLine()

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (msg in messages) {
            val formattedTime = timeFormat.format(Date(msg.timestamp))
            val roleLabel = if (msg.role == "user") "🧑 User" else "⚡ Venice AI"
            sb.appendLine("### $roleLabel ($formattedTime)")
            sb.appendLine(msg.text)
            if (msg.citedNotes.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("**References:** " + msg.citedNotes.joinToString(", ") { "[[$it]]" })
            }
            if (msg.executedTools.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("```system_tools_executed")
                msg.executedTools.forEach { tool ->
                    sb.appendLine("- ${tool.action}: ${tool.target} -> ${tool.details}")
                }
                sb.appendLine("```")
            }
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        exportFile.writeText(sb.toString())
        exportFile
    }

    private fun defaultInitialMessage(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = "model",
                text = "**Venice AI Wiki Coprocessor initialized.**\n" +
                        "Operating mode: *Raw, Objective & Direct*. High signal-to-noise ratio.\n" +
                        "Direct storage integration active: `/storage/emulated/0/Download/ObsidianVault`.\n" +
                        "Database, Auth Config, Chat History & Markdown files persist directly on disk."
            )
        )
    }
}
