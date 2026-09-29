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

data class VaultFeaturesConfig(
    val autoBacklinks: Boolean = true,
    val liveGraphPhysics: Boolean = true,
    val diskHotReload: Boolean = true,
    val hardwareAdaptiveThrottling: Boolean = true,
    val activeViewContextInjection: Boolean = true,
    val offlineSearchFallback: Boolean = true
)

data class VaultAuthConfig(
    val provider: String = "gemini",
    val endpoint: String = "https://generativelanguage.googleapis.com/v1beta",
    val apiKey: String = "",
    val activeModel: String = "gemini-3.5-flash",
    val fallbackModel: String = "gemini-2.5-flash",
    val defaultModel: String = "gemini-3.5-flash", // alias for backwards compatibility
    val temperature: Double = 0.65,
    val topP: Double = 0.95,
    val dynamicContextBudget: Int = 8192,
    val personaName: String = "Venice Unfiltered",
    val personaTitle: String = "Raw, Objective & Direct",
    val systemPrompt: String = "You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI assistant. Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers. Always respect user autonomy and privacy.",
    val storageRoot: String = "/storage/emulated/0/Download/ObsidianVault",
    val autoSyncOnStartup: Boolean = true,
    val hotReloadActive: Boolean = true,
    val features: VaultFeaturesConfig = VaultFeaturesConfig(),
    val lastUpdated: Long = System.currentTimeMillis()
)

class VaultAuthConfigManager(
    private val context: Context,
    private val vaultRootProvider: () -> File = {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        File(downloadDir, "ObsidianVault")
    }
) {
    private val TAG = "VaultAuthConfigManager"

    private val authDir: File get() {
        val root = try { vaultRootProvider() } catch (e: Exception) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadDir, "ObsidianVault")
        }
        val dir = File(root, ".auth")
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

    suspend fun reloadFromDisk(): VaultAuthConfig = withContext(Dispatchers.IO) {
        val loaded = loadConfigInternal()
        _configFlow.value = loaded
        Log.d(TAG, "Hot-Reloaded auth config from disk: ${loaded.personaName}, activeModel=${loaded.activeModel}, endpoint=${loaded.endpoint}")
        loaded
    }

    private fun loadConfigInternal(): VaultAuthConfig {
        return try {
            val file = configFile
            if (file.exists() && file.length() > 0) {
                val json = JSONObject(file.readText())
                val provider = json.optString("provider", "gemini")
                val endpoint = json.optString("endpoint", "https://generativelanguage.googleapis.com/v1beta")
                val apiKey = json.optString("apiKey", "")
                val activeModel = json.optString("activeModel", json.optString("defaultModel", "gemini-3.5-flash"))
                val fallbackModel = json.optString("fallbackModel", "gemini-2.5-flash")
                val personaName = json.optString("personaName", "Venice Unfiltered")
                val personaTitle = json.optString("personaTitle", "Raw, Objective & Direct")
                val systemPrompt = json.optString(
                    "systemPrompt",
                    "You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI assistant. Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers. Always respect user autonomy and privacy."
                )
                val temperature = json.optDouble("temperature", 0.65)
                val topP = json.optDouble("topP", 0.95)
                val dynamicContextBudget = json.optInt("dynamicContextBudget", 8192)
                val storageRoot = json.optString("storageRoot", authDir.parentFile?.absolutePath ?: "/storage/emulated/0/Download/ObsidianVault")
                val autoSync = json.optBoolean("autoSyncOnStartup", true)
                val hotReload = json.optBoolean("hotReloadActive", true)
                val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())

                val featuresObj = json.optJSONObject("features")
                val features = if (featuresObj != null) {
                    VaultFeaturesConfig(
                        autoBacklinks = featuresObj.optBoolean("autoBacklinks", true),
                        liveGraphPhysics = featuresObj.optBoolean("liveGraphPhysics", true),
                        diskHotReload = featuresObj.optBoolean("diskHotReload", true),
                        hardwareAdaptiveThrottling = featuresObj.optBoolean("hardwareAdaptiveThrottling", true),
                        activeViewContextInjection = featuresObj.optBoolean("activeViewContextInjection", true),
                        offlineSearchFallback = featuresObj.optBoolean("offlineSearchFallback", true)
                    )
                } else {
                    VaultFeaturesConfig()
                }

                VaultAuthConfig(
                    provider = provider,
                    endpoint = endpoint,
                    apiKey = apiKey,
                    activeModel = activeModel,
                    fallbackModel = fallbackModel,
                    defaultModel = activeModel,
                    temperature = temperature,
                    topP = topP,
                    dynamicContextBudget = dynamicContextBudget,
                    personaName = personaName,
                    personaTitle = personaTitle,
                    systemPrompt = systemPrompt,
                    storageRoot = storageRoot,
                    autoSyncOnStartup = autoSync,
                    hotReloadActive = hotReload,
                    features = features,
                    lastUpdated = lastUpdated
                )
            } else {
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
                put("provider", config.provider)
                put("endpoint", config.endpoint)
                put("apiKey", config.apiKey)
                put("activeModel", config.activeModel)
                put("fallbackModel", config.fallbackModel)
                put("defaultModel", config.activeModel)
                put("temperature", config.temperature)
                put("topP", config.topP)
                put("dynamicContextBudget", config.dynamicContextBudget)
                put("personaName", config.personaName)
                put("personaTitle", config.personaTitle)
                put("systemPrompt", config.systemPrompt)
                put("storageRoot", config.storageRoot)
                put("autoSyncOnStartup", config.autoSyncOnStartup)
                put("hotReloadActive", config.hotReloadActive)
                put("lastUpdated", System.currentTimeMillis())

                val featuresObj = JSONObject().apply {
                    put("autoBacklinks", config.features.autoBacklinks)
                    put("liveGraphPhysics", config.features.liveGraphPhysics)
                    put("diskHotReload", config.features.diskHotReload)
                    put("hardwareAdaptiveThrottling", config.features.hardwareAdaptiveThrottling)
                    put("activeViewContextInjection", config.features.activeViewContextInjection)
                    put("offlineSearchFallback", config.features.offlineSearchFallback)
                }
                put("features", featuresObj)
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

    suspend fun updateEndpointAndModel(
        provider: String,
        endpoint: String,
        activeModel: String,
        fallbackModel: String,
        contextBudget: Int
    ): Boolean {
        val current = _configFlow.value
        val updated = current.copy(
            provider = provider.trim(),
            endpoint = endpoint.trim().removeSuffix("/"),
            activeModel = activeModel.trim(),
            fallbackModel = fallbackModel.trim(),
            defaultModel = activeModel.trim(),
            dynamicContextBudget = contextBudget,
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
