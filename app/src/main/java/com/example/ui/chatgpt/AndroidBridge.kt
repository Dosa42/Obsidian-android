package com.example.ui.chatgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.example.data.auth.ChatGPTAuthManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AndroidBridge(
    private val context: Context,
    private val chatGPTAuthManager: ChatGPTAuthManager,
    private val scope: CoroutineScope,
    private val webViewProvider: () -> WebView?
) {
    private val TAG = "AndroidBridge"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeStreams = ConcurrentHashMap<String, Call>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @JavascriptInterface
    fun nativeHttpRequest(requestId: String, url: String, method: String, headersJson: String, body: String?) {
        scope.launch(Dispatchers.IO) {
            try {
                val reqBuilder = Request.Builder().url(url)

                if (headersJson.isNotBlank()) {
                    try {
                        val hObj = JSONObject(headersJson)
                        val keys = hObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = hObj.getString(k)
                            reqBuilder.header(k, v)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed parsing headers JSON", e)
                    }
                }

                if (method.equals("POST", ignoreCase = true)) {
                    val mediaType = (reqBuilder.build().header("Content-Type") ?: "application/json").toMediaType()
                    val reqBody = (body ?: "").toRequestBody(mediaType)
                    reqBuilder.post(reqBody)
                } else if (method.equals("PUT", ignoreCase = true)) {
                    val mediaType = (reqBuilder.build().header("Content-Type") ?: "application/json").toMediaType()
                    val reqBody = (body ?: "").toRequestBody(mediaType)
                    reqBuilder.put(reqBody)
                } else if (method.equals("DELETE", ignoreCase = true)) {
                    reqBuilder.delete()
                } else {
                    reqBuilder.get()
                }

                val response = okHttpClient.newCall(reqBuilder.build()).execute()
                val statusCode = response.code
                val responseText = response.body?.string() ?: ""

                mainHandler.post {
                    val jsCallback = "if (window['__http_cb_$requestId']) { window['__http_cb_$requestId']($statusCode, ${JSONObject.quote(responseText)}, null); }"
                    webViewProvider()?.evaluateJavascript(jsCallback, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native HTTP request failed: $url", e)
                val errMsg = e.message ?: "Network request failed"
                mainHandler.post {
                    val jsCallback = "if (window['__http_cb_$requestId']) { window['__http_cb_$requestId'](0, null, ${JSONObject.quote(errMsg)}); }"
                    webViewProvider()?.evaluateJavascript(jsCallback, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun startStreamingRequest(streamId: String, url: String, headersJson: String, payloadJson: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val reqBuilder = Request.Builder().url(url)
                if (headersJson.isNotBlank()) {
                    try {
                        val hObj = JSONObject(headersJson)
                        val keys = hObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            reqBuilder.header(k, hObj.getString(k))
                        }
                    } catch (_: Exception) {}
                }

                val reqBody = payloadJson.toRequestBody("application/json".toMediaType())
                reqBuilder.post(reqBody)

                val call = okHttpClient.newCall(reqBuilder.build())
                activeStreams[streamId] = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw IOException("HTTP ${response.code}: $errBody")
                }

                val source = response.body?.source() ?: throw IOException("Empty response stream")
                var line: String? = null

                while (isActive && source.readUtf8Line().also { line = it } != null) {
                    val l = line ?: continue
                    mainHandler.post {
                        val jsCallback = "if (window['__stream_chunk_$streamId']) { window['__stream_chunk_$streamId'](${JSONObject.quote(l)}); }"
                        webViewProvider()?.evaluateJavascript(jsCallback, null)
                    }
                }

                mainHandler.post {
                    val jsEnd = "if (window['__stream_end_$streamId']) { window['__stream_end_$streamId'](false, null); }"
                    webViewProvider()?.evaluateJavascript(jsEnd, null)
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: "Streaming failed"
                mainHandler.post {
                    val jsEnd = "if (window['__stream_end_$streamId']) { window['__stream_end_$streamId'](true, ${JSONObject.quote(errMsg)}); }"
                    webViewProvider()?.evaluateJavascript(jsEnd, null)
                }
            } finally {
                activeStreams.remove(streamId)
            }
        }
    }

    @JavascriptInterface
    fun cancelStreaming(streamId: String) {
        activeStreams[streamId]?.cancel()
        activeStreams.remove(streamId)
    }

    @JavascriptInterface
    fun startLoopbackServer() {
        chatGPTAuthManager.startLoopbackServer { success, email ->
            mainHandler.post {
                if (success) {
                    val callbackUrl = "http://localhost:1455/auth/callback"
                    val jsNotify = "if (window.onOAuthCallbackReceived) { window.onOAuthCallbackReceived('$callbackUrl'); }"
                    webViewProvider()?.evaluateJavascript(jsNotify, null)
                }
            }
        }
    }

    @JavascriptInterface
    fun getNativeSessionJson(): String {
        val session = chatGPTAuthManager.sessionState.value ?: chatGPTAuthManager.loadSessionFromDisk()
        return if (session != null && session.isValid) {
            JSONObject().apply {
                put("accessToken", session.accessToken)
                put("refreshToken", session.refreshToken)
                put("idToken", session.idToken)
                put("accountId", session.accountId)
                put("email", session.email)
                put("clientId", session.clientId)
                put("expiresAt", session.expiresAt)
                put("refreshedAt", session.refreshedAt)
            }.toString()
        } else {
            ""
        }
    }

    @JavascriptInterface
    fun saveNativeSession(sessionJsonStr: String) {
        try {
            if (sessionJsonStr.isNotBlank()) {
                val json = JSONObject(sessionJsonStr)
                val session = com.example.data.auth.ChatGPTSession(
                    accessToken = json.optString("accessToken", ""),
                    refreshToken = json.optString("refreshToken", ""),
                    idToken = json.optString("idToken", ""),
                    accountId = json.optString("accountId", ""),
                    email = json.optString("email", ""),
                    clientId = json.optString("clientId", ChatGPTAuthManager.PUBLIC_CLIENT_ID),
                    expiresAt = json.optLong("expiresAt", 0L),
                    refreshedAt = json.optLong("refreshedAt", System.currentTimeMillis())
                )
                chatGPTAuthManager.saveSessionToDisk(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving native session from bridge", e)
        }
    }

    @JavascriptInterface
    fun clearNativeSession() {
        chatGPTAuthManager.clearSession()
    }

    @JavascriptInterface
    fun getVaultChatHistoryJson(): String {
        return try {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val vaultDir = File(downloadDir, "ObsidianVault")
            val chatFile = File(File(vaultDir, ".chat"), "chat_history.json")
            if (chatFile.exists()) {
                chatFile.readText()
            } else {
                "[]"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading vault chat history", e)
            "[]"
        }
    }

    @JavascriptInterface
    fun saveMessageToVault(role: String, text: String, model: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val vaultDir = File(downloadDir, "ObsidianVault")
                val chatDir = File(vaultDir, ".chat").apply { if (!exists()) mkdirs() }
                val chatFile = File(chatDir, "chat_history.json")
                val list = if (chatFile.exists() && chatFile.length() > 0) {
                    try { org.json.JSONArray(chatFile.readText()) } catch (_: Exception) { org.json.JSONArray() }
                } else {
                    org.json.JSONArray()
                }

                val msgObj = JSONObject().apply {
                    put("id", java.util.UUID.randomUUID().toString())
                    put("role", role)
                    put("text", text)
                    put("timestamp", System.currentTimeMillis())
                    put("isSynthesizing", false)
                    put("citedNotes", org.json.JSONArray())
                    put("executedTools", org.json.JSONArray())
                }
                list.put(msgObj)
                chatFile.writeText(list.toString(2))
            } catch (e: Exception) {
                Log.e(TAG, "Failed saving message to vault", e)
            }
        }
    }

    @JavascriptInterface
    fun stopLoopbackServer() {
        chatGPTAuthManager.stopLoopbackServer()
    }

    @JavascriptInterface
    fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed opening external URL", e)
        }
    }

    @JavascriptInterface
    fun exportMarkdown(fileName: String, markdownContent: String) {
        try {
            val downloadDir = context.getExternalFilesDir(null) ?: context.filesDir
            val exportFile = File(downloadDir, fileName)
            exportFile.writeText(markdownContent)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, markdownContent)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export Markdown Note").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed exporting markdown", e)
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Obsidian ChatGPT", text)
            clipboard?.setPrimaryClip(clip)
            mainHandler.post {
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying to clipboard", e)
        }
    }
}
