package com.example.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.example.data.model.ChatMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ChatGPTSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val idToken: String = "",
    val accountId: String = "",
    val email: String = "",
    val clientId: String = "app_EMoamEEZ73f0CkXaXp7hrann",
    val expiresAt: Long = 0L,
    val refreshedAt: Long = 0L
) {
    val isValid: Boolean get() = accessToken.isNotBlank() && (expiresAt == 0L || System.currentTimeMillis() < expiresAt)
}

data class ChatGPTModelInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val reasoningLevels: List<String> = emptyList()
)

data class PendingOAuth(
    val state: String,
    val verifier: String,
    val clientId: String,
    val redirectUri: String,
    val timestamp: Long
)

class ChatGPTAuthManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "ChatGPTAuthManager"

    companion object {
        const val AUTH_URL = "https://auth.openai.com/oauth/authorize"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val API_BASE = "https://chatgpt.com/backend-api/codex"
        const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        const val PUBLIC_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val CODEX_VERSION = "0.155.1"
        const val SCOPE = "openid profile email offline_access api.connectors.read api.connectors.invoke"
        const val LOOPBACK_PORT = 1455
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val authDir: File get() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vaultDir = File(downloadDir, "ObsidianVault")
        val dir = File(vaultDir, ".auth")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private val sessionFile: File get() = File(authDir, "chatgpt_session.json")

    private val _sessionState = MutableStateFlow<ChatGPTSession?>(null)
    val sessionState: StateFlow<ChatGPTSession?> = _sessionState.asStateFlow()

    private val _models = MutableStateFlow<List<ChatGPTModelInfo>>(
        listOf(
            ChatGPTModelInfo("gpt-4o", "GPT-4o (Omni)"),
            ChatGPTModelInfo("gpt-4o-mini", "GPT-4o Mini"),
            ChatGPTModelInfo("o1", "o1 (Reasoning)"),
            ChatGPTModelInfo("o3-mini", "o3-mini")
        )
    )
    val models: StateFlow<List<ChatGPTModelInfo>> = _models.asStateFlow()

    private var pendingOAuth: PendingOAuth? = null
    private var loopbackServerSocket: ServerSocket? = null
    private var loopbackJob: Job? = null
    private val activeStreams = ConcurrentHashMap<String, Call>()

    init {
        loadSessionFromDisk()
    }

    fun loadSessionFromDisk(): ChatGPTSession? {
        return try {
            if (sessionFile.exists()) {
                val json = JSONObject(sessionFile.readText())
                val session = ChatGPTSession(
                    accessToken = json.optString("accessToken", ""),
                    refreshToken = json.optString("refreshToken", ""),
                    idToken = json.optString("idToken", ""),
                    accountId = json.optString("accountId", ""),
                    email = json.optString("email", ""),
                    clientId = json.optString("clientId", PUBLIC_CLIENT_ID),
                    expiresAt = json.optLong("expiresAt", 0L),
                    refreshedAt = json.optLong("refreshedAt", 0L)
                )
                _sessionState.value = session
                // If near expiration, auto-refresh
                if (session.refreshToken.isNotBlank() && session.expiresAt > 0 && System.currentTimeMillis() >= session.expiresAt - 120_000) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            refreshToken()
                        } catch (e: Exception) {
                            Log.w(TAG, "Auto token refresh failed on startup", e)
                        }
                    }
                }
                session
            } else {
                _sessionState.value = null
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading chatgpt_session.json", e)
            _sessionState.value = null
            null
        }
    }

    fun saveSessionToDisk(session: ChatGPTSession) {
        try {
            val json = JSONObject().apply {
                put("accessToken", session.accessToken)
                put("refreshToken", session.refreshToken)
                put("idToken", session.idToken)
                put("accountId", session.accountId)
                put("email", session.email)
                put("clientId", session.clientId)
                put("expiresAt", session.expiresAt)
                put("refreshedAt", session.refreshedAt)
            }
            sessionFile.writeText(json.toString(2))
            _sessionState.value = session
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving chatgpt_session.json", e)
        }
    }

    fun clearSession() {
        try {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
            _sessionState.value = null
            pendingOAuth = null
            stopLoopbackServer()
        } catch (e: Exception) {
            Log.e(TAG, "Failed clearing chatgpt session", e)
        }
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateRandomString(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        return base64UrlEncode(bytes)
    }

    private fun generatePkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return base64UrlEncode(hash)
    }

    /**
     * Initiates OAuth 2.0 PKCE Flow:
     * Starts loopback server on localhost:1455 and launches system browser.
     */
    fun initiateLogin(customClientId: String? = null, openBrowser: Boolean = true): String {
        val clientId = customClientId?.trim()?.ifBlank { null } ?: PUBLIC_CLIENT_ID
        val verifier = generateRandomString(32)
        val state = generateRandomString(32)
        val challenge = generatePkceChallenge(verifier)

        pendingOAuth = PendingOAuth(
            state = state,
            verifier = verifier,
            clientId = clientId,
            redirectUri = REDIRECT_URI,
            timestamp = System.currentTimeMillis()
        )

        val uriBuilder = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("originator", "codex_cli_rs")

        val authUri = uriBuilder.build()
        val fullUrl = authUri.toString()

        startLoopbackServer()

        if (openBrowser) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching external browser for OAuth PKCE", e)
            }
        }

        return fullUrl
    }

    fun startLoopbackServer(onAuthCompleted: ((Boolean, String) -> Unit)? = null) {
        stopLoopbackServer()

        loopbackJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(LOOPBACK_PORT)
                loopbackServerSocket = server
                Log.d(TAG, "Loopback OAuth server listening on port $LOOPBACK_PORT")

                while (isActive && !server.isClosed) {
                    val clientSocket = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }

                    launch(Dispatchers.IO) {
                        handleLoopbackClient(clientSocket, onAuthCompleted)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Loopback server could not bind or was closed", e)
            }
        }
    }

    fun stopLoopbackServer() {
        try {
            loopbackJob?.cancel()
            loopbackJob = null
            loopbackServerSocket?.close()
            loopbackServerSocket = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing loopback server", e)
        }
    }

    private suspend fun handleLoopbackClient(socket: Socket, onAuthCompleted: ((Boolean, String) -> Unit)?) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size >= 2) {
                val path = parts[1]
                if (path.startsWith("/auth/callback")) {
                    val fullUrl = "http://localhost:$LOOPBACK_PORT$path"
                    val (code, state) = parseCallbackUrl(fullUrl)

                    val exchangeResult = completeLoginWithCode(code, state)

                    val responseHtml = if (exchangeResult.isSuccess) {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <title>ChatGPT Authentication Successful</title>
                            <style>
                                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #18181b; color: #f4f4f5; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; text-align: center; }
                                .card { background: #27272a; padding: 32px; border-radius: 16px; border: 1px solid #3f3f46; box-shadow: 0 10px 25px rgba(0,0,0,0.5); max-width: 380px; }
                                h2 { color: #10b981; margin-top: 0; }
                                p { color: #a1a1aa; font-size: 14px; line-height: 1.5; }
                                .badge { background: #06b6d4; color: #09090b; padding: 4px 10px; border-radius: 999px; font-weight: bold; font-size: 12px; display: inline-block; margin-top: 12px; }
                            </style>
                        </head>
                        <body>
                            <div class="card">
                                <h2>✦ Obsidian Vault</h2>
                                <div class="badge">OAuth 2.0 PKCE Verified</div>
                                <p>Successfully authenticated with ChatGPT Codex!<br>You may close this tab and return to Obsidian Vault.</p>
                            </div>
                        </body>
                        </html>
                        """.trimIndent()
                    } else {
                        """
                        <!DOCTYPE html>
                        <html>
                        <head><title>OAuth Failed</title></head>
                        <body style="font-family: sans-serif; background: #18181b; color: #ef4444; padding: 24px;">
                            <h3>OAuth Verification Failed</h3>
                            <p>${exchangeResult.exceptionOrNull()?.message}</p>
                        </body>
                        </html>
                        """.trimIndent()
                    }

                    val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
                    writer.write("HTTP/1.1 200 OK\r\n")
                    writer.write("Content-Type: text/html; charset=UTF-8\r\n")
                    writer.write("Content-Length: ${responseHtml.toByteArray(StandardCharsets.UTF_8).size}\r\n")
                    writer.write("Connection: close\r\n\r\n")
                    writer.write(responseHtml)
                    writer.flush()

                    onAuthCompleted?.invoke(exchangeResult.isSuccess, exchangeResult.getOrNull()?.email ?: "")
                    stopLoopbackServer()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling loopback client", e)
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun parseCallbackUrl(rawUrl: String): Pair<String, String> {
        val uri = Uri.parse(rawUrl.trim())
        val error = uri.getQueryParameter("error")
        if (error != null) {
            val desc = uri.getQueryParameter("error_description") ?: error
            throw IllegalArgumentException("OAuth error: $desc")
        }
        val code = uri.getQueryParameter("code") ?: throw IllegalArgumentException("Missing code parameter in callback URL")
        val state = uri.getQueryParameter("state") ?: throw IllegalArgumentException("Missing state parameter in callback URL")
        return Pair(code, state)
    }

    suspend fun completeLoginWithUrl(rawUrl: String): Result<ChatGPTSession> = withContext(Dispatchers.IO) {
        try {
            val (code, state) = parseCallbackUrl(rawUrl)
            completeLoginWithCode(code, state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeLoginWithCode(code: String, state: String): Result<ChatGPTSession> = withContext(Dispatchers.IO) {
        try {
            val pending = pendingOAuth ?: throw IllegalStateException("No pending OAuth session found. Please initiate sign-in again.")
            if (pending.state != state) {
                throw IllegalStateException("State mismatch during PKCE verification.")
            }

            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", pending.clientId)
                .add("redirect_uri", pending.redirectUri)
                .add("code", code)
                .add("code_verifier", pending.verifier)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val j = JSONObject(responseBody)
                    j.optString("error_description", j.optString("error", responseBody))
                } catch (_: Exception) {
                    responseBody
                }
                throw IOException("Token exchange failed (${response.code}): $errorMsg")
            }

            val tokenJson = JSONObject(responseBody)
            val accessToken = tokenJson.optString("access_token")
            val refreshToken = tokenJson.optString("refresh_token")
            val idToken = tokenJson.optString("id_token")
            val expiresIn = tokenJson.optLong("expires_in", 3600L)

            if (accessToken.isBlank()) {
                throw IOException("Token endpoint returned no access_token.")
            }

            val accessClaims = parseJwt(accessToken)
            val idClaims = parseJwt(idToken)
            val authClaim = idClaims.optJSONObject("https://api.openai.com/auth") ?: accessClaims.optJSONObject("https://api.openai.com/auth")
            val profileClaim = accessClaims.optJSONObject("https://api.openai.com/profile")

            val accountId = authClaim?.optString("chatgpt_account_id", "") ?: ""
            val email = idClaims.optString("email", profileClaim?.optString("email", accessClaims.optString("email", "ChatGPT User")))
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

            val session = ChatGPTSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                idToken = idToken,
                accountId = accountId,
                email = email,
                clientId = pending.clientId,
                expiresAt = expiresAt,
                refreshedAt = System.currentTimeMillis()
            )

            saveSessionToDisk(session)
            pendingOAuth = null

            // Fetch available models in background
            launch {
                try {
                    fetchModels()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching model list", e)
                }
            }

            Result.success(session)
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed", e)
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Result<ChatGPTSession> = withContext(Dispatchers.IO) {
        val current = _sessionState.value ?: loadSessionFromDisk() ?: return@withContext Result.failure(IllegalStateException("No session to refresh"))
        if (current.refreshToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No refresh token available"))
        }

        try {
            val jsonPayload = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("client_id", current.clientId.ifBlank { PUBLIC_CLIENT_ID })
                put("refresh_token", current.refreshToken)
            }

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw IOException("Token refresh failed: $responseBody")
            }

            val tokenJson = JSONObject(responseBody)
            val newAccess = tokenJson.optString("access_token")
            val newRefresh = tokenJson.optString("refresh_token", current.refreshToken)
            val newId = tokenJson.optString("id_token", current.idToken)
            val expiresIn = tokenJson.optLong("expires_in", 3600L)

            val accessClaims = parseJwt(newAccess)
            val idClaims = parseJwt(newId)
            val authClaim = idClaims.optJSONObject("https://api.openai.com/auth") ?: accessClaims.optJSONObject("https://api.openai.com/auth")

            val updated = current.copy(
                accessToken = newAccess,
                refreshToken = newRefresh,
                idToken = newId,
                accountId = authClaim?.optString("chatgpt_account_id", current.accountId) ?: current.accountId,
                expiresAt = System.currentTimeMillis() + (expiresIn * 1000),
                refreshedAt = System.currentTimeMillis()
            )

            saveSessionToDisk(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Log.e(TAG, "Refresh token request failed", e)
            Result.failure(e)
        }
    }

    fun getApiHeaders(): Map<String, String> {
        val session = _sessionState.value ?: loadSessionFromDisk() ?: throw IllegalStateException("Not authenticated with ChatGPT OAuth")
        val map = mutableMapOf<String, String>()
        map["Authorization"] = "Bearer ${session.accessToken}"
        map["originator"] = "codex_cli_rs"
        if (session.accountId.isNotBlank()) {
            map["ChatGPT-Account-ID"] = session.accountId
        }
        return map
    }

    suspend fun fetchModels(): List<ChatGPTModelInfo> = withContext(Dispatchers.IO) {
        val headers = getApiHeaders()
        val url = "$API_BASE/models?client_version=$CODEX_VERSION"

        val reqBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        var resp = okHttpClient.newCall(reqBuilder.build()).execute()
        var body = resp.body?.string() ?: ""

        if (resp.code == 401) {
            // Attempt refresh once
            refreshToken()
            val newHeaders = getApiHeaders()
            val retryReq = Request.Builder().url(url)
            newHeaders.forEach { (k, v) -> retryReq.header(k, v) }
            resp = okHttpClient.newCall(retryReq.build()).execute()
            body = resp.body?.string() ?: ""
        }

        if (!resp.isSuccessful) {
            throw IOException("Fetch models failed: $body")
        }

        val json = JSONObject(body)
        val arr = json.optJSONArray("models") ?: json.optJSONArray("data") ?: JSONArray()
        val list = mutableListOf<ChatGPTModelInfo>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val id = m.optString("slug", m.optString("id"))
            val name = m.optString("display_name", m.optString("name", id))
            val desc = m.optString("description", "")
            val reasonArr = m.optJSONArray("supported_reasoning_levels")
            val reasonLevels = mutableListOf<String>()
            if (reasonArr != null) {
                for (j in 0 until reasonArr.length()) {
                    reasonLevels.add(reasonArr.getString(j))
                }
            }
            list.add(ChatGPTModelInfo(id, name, desc, reasonLevels))
        }

        if (list.isNotEmpty()) {
            _models.value = list
        }
        list
    }

    /**
     * Executes real-time SSE streaming against ChatGPT Codex Responses API.
     */
    suspend fun streamResponses(
        model: String,
        messages: List<ChatMessage>,
        userPrompt: String,
        systemInstructions: String,
        reasoningEffort: String? = null,
        onChunk: (String) -> Unit,
        onStatus: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        var session = _sessionState.value ?: loadSessionFromDisk() ?: throw IllegalStateException("Please sign in with ChatGPT OAuth PKCE first.")
        if (session.expiresAt > 0 && System.currentTimeMillis() >= session.expiresAt - 90_000) {
            val refreshRes = refreshToken()
            session = refreshRes.getOrNull() ?: session
        }

        val url = "$API_BASE/responses"
        val headers = getApiHeaders()

        // Build Codex payload
        val inputList = JSONArray()
        for (m in messages) {
            val msgObj = JSONObject().apply {
                put("type", "message")
                put("role", if (m.role == "user") "user" else "assistant")
                val contentArr = JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", if (m.role == "user") "input_text" else "output_text")
                        put("text", m.text)
                    })
                }
                put("content", contentArr)
            }
            inputList.put(msgObj)
        }

        // Add user prompt
        inputList.put(JSONObject().apply {
            put("type", "message")
            put("role", "user")
            val contentArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "input_text")
                    put("text", userPrompt)
                })
            }
            put("content", contentArr)
        })

        val payload = JSONObject().apply {
            put("model", model)
            put("instructions", systemInstructions)
            put("input", inputList)
            put("stream", true)
            put("store", false)
            put("tools", JSONArray())
            put("parallel_tool_calls", false)
            if (!reasoningEffort.isNullOrBlank()) {
                put("reasoning", JSONObject().put("effort", reasoningEffort))
            }
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                header("Content-Type", "application/json")
                header("Accept", "text/event-stream")
            }
            .build()

        val streamId = "stream_${System.currentTimeMillis()}"
        val call = okHttpClient.newCall(request)
        activeStreams[streamId] = call

        val accumulatedText = StringBuilder()

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw IOException("ChatGPT API error (${response.code}): $errBody")
            }

            val source = response.body?.source() ?: throw IOException("Empty response stream")
            var line: String? = null

            while (isActive && source.readUtf8Line().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data: ")) {
                    val dataStr = currentLine.substring(6).trim()
                    if (dataStr == "[DONE]") break

                    try {
                        val event = JSONObject(dataStr)
                        val type = event.optString("type")

                        if (type == "response.output_text.delta" || type == "response.refusal.delta") {
                            val delta = event.optString("delta", "")
                            if (delta.isNotEmpty()) {
                                accumulatedText.append(delta)
                                onChunk(delta)
                                onStatus("Receiving reply...")
                            }
                        } else if (type == "response.created" || type == "response.in_progress" || type.startsWith("response.reasoning")) {
                            onStatus("Thinking...")
                        } else if (type == "response.completed") {
                            val respObj = event.optJSONObject("response")
                            val outputArr = respObj?.optJSONArray("output")
                            if (outputArr != null && accumulatedText.isEmpty()) {
                                for (i in 0 until outputArr.length()) {
                                    val item = outputArr.getJSONObject(i)
                                    val content = item.optJSONArray("content")
                                    if (content != null) {
                                        for (j in 0 until content.length()) {
                                            val c = content.getJSONObject(j)
                                            if (c.optString("type") == "output_text") {
                                                val text = c.optString("text")
                                                accumulatedText.append(text)
                                                onChunk(text)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (type == "error" || type == "response.failed") {
                            val err = event.optJSONObject("error")?.optString("message") ?: "Stream error"
                            throw IOException(err)
                        }
                    } catch (_: Exception) {}
                }
            }
        } finally {
            activeStreams.remove(streamId)
        }

        return@withContext accumulatedText.toString()
    }

    fun cancelStream(streamId: String) {
        activeStreams[streamId]?.cancel()
        activeStreams.remove(streamId)
    }

    private fun parseJwt(token: String): JSONObject {
        if (token.isBlank()) return JSONObject()
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                var base64 = parts[1].replace("-", "+").replace("_", "/")
                while (base64.length % 4 != 0) base64 += "="
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                JSONObject(String(decoded, StandardCharsets.UTF_8))
            } else {
                JSONObject()
            }
        } catch (_: Exception) {
            JSONObject()
        }
    }
}
