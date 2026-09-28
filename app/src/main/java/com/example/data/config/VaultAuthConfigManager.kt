package com.example.data.config

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class VaultAuthConfig(
    val apiKey: String = "",
    val personaName: String = "Venice Unfiltered",
    val personaTitle: String = "Raw, Objective & Direct",
    val systemPrompt: String = "You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI assistant. Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers. Always respect user autonomy and privacy.",
    val defaultModel: String = "gemini-3.5-flash",
    val temperature: Double = 0.7,
    val topP: Double = 0.95,
    val storageRoot: String = "/storage/emulated/0/Download/ObsidianVault",
    val autoSyncOnStartup: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

class VaultAuthConfigManager(private val context: Context) {
    private val TAG = "VaultAuthConfigManager"

    private val authDir: File get() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vaultDir = File(downloadDir, "ObsidianVault")
        val dir = File(vaultDir, ".auth")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    val configFile: File get() = File(authDir, "vault_auth_config.json")
    val configAbsolutePath: String get() = configFile.absolutePath

    private val _configFlow = MutableStateFlow(VaultAuthConfig())
    val configFlow: StateFlow<VaultAuthConfig> = _configFlow.asStateFlow()

    suspend fun initialize(): VaultAuthConfig = withContext(Dispatchers.IO) {
        val loaded = loadConfigInternal()
        _configFlow.value = loaded
        loaded
    }

    private fun loadConfigInternal(): VaultAuthConfig {
        return try {
            val file = configFile
            if (file.exists() && file.length() > 0) {
                val json = JSONObject(file.readText())
                val apiKey = json.optString("apiKey", "")
                val personaName = json.optString("personaName", "Venice Unfiltered")
                val personaTitle = json.optString("personaTitle", "Raw, Objective & Direct")
                val systemPrompt = json.optString(
                    "systemPrompt",
                    "You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI assistant. Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers. Always respect user autonomy and privacy."
                )
                val defaultModel = json.optString("defaultModel", "gemini-3.5-flash")
                val temperature = json.optDouble("temperature", 0.7)
                val topP = json.optDouble("topP", 0.95)
                val storageRoot = json.optString("storageRoot", authDir.parentFile?.absolutePath ?: "/storage/emulated/0/Download/ObsidianVault")
                val autoSync = json.optBoolean("autoSyncOnStartup", true)
                val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())

                VaultAuthConfig(
                    apiKey = apiKey,
                    personaName = personaName,
                    personaTitle = personaTitle,
                    systemPrompt = systemPrompt,
                    defaultModel = defaultModel,
                    temperature = temperature,
                    topP = topP,
                    storageRoot = storageRoot,
                    autoSyncOnStartup = autoSync,
                    lastUpdated = lastUpdated
                )
            } else {
                // Initialize default config file on /storage/emulated/0/Download/ObsidianVault/.auth/
                val defaultConfig = VaultAuthConfig(
                    apiKey = if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") BuildConfig.GEMINI_API_KEY else "",
                    storageRoot = authDir.parentFile?.absolutePath ?: "/storage/emulated/0/Download/ObsidianVault"
                )
                saveConfigInternal(defaultConfig)
                defaultConfig
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading auth config from ${configFile.absolutePath}", e)
            VaultAuthConfig()
        }
    }

    suspend fun saveConfig(config: VaultAuthConfig): Boolean = withContext(Dispatchers.IO) {
        val success = saveConfigInternal(config)
        if (success) {
            _configFlow.value = config
        }
        success
    }

    private fun saveConfigInternal(config: VaultAuthConfig): Boolean {
        return try {
            val file = configFile
            file.parentFile?.mkdirs()

            val json = JSONObject().apply {
                put("apiKey", config.apiKey)
                put("personaName", config.personaName)
                put("personaTitle", config.personaTitle)
                put("systemPrompt", config.systemPrompt)
                put("defaultModel", config.defaultModel)
                put("temperature", config.temperature)
                put("topP", config.topP)
                put("storageRoot", config.storageRoot)
                put("autoSyncOnStartup", config.autoSyncOnStartup)
                put("lastUpdated", System.currentTimeMillis())
            }

            file.writeText(json.toString(2))
            Log.d(TAG, "Saved auth & persona config to ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auth config to ${configFile.absolutePath}", e)
            false
        }
    }

    suspend fun updateApiKey(apiKey: String): Boolean {
        val current = _configFlow.value
        val updated = current.copy(apiKey = apiKey.trim(), lastUpdated = System.currentTimeMillis())
        return saveConfig(updated)
    }

    suspend fun updatePersona(
        name: String,
        title: String,
        prompt: String,
        temperature: Double
    ): Boolean {
        val current = _configFlow.value
        val updated = current.copy(
            personaName = name.trim(),
            personaTitle = title.trim(),
            systemPrompt = prompt.trim(),
            temperature = temperature,
            lastUpdated = System.currentTimeMillis()
        )
        return saveConfig(updated)
    }

    fun getEffectiveApiKey(): String {
        val customKey = _configFlow.value.apiKey
        if (customKey.isNotBlank()) return customKey
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") return buildKey
        return ""
    }
}
