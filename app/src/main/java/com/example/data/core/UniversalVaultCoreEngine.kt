package com.example.data.core

import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/* =================================================================================================
 * 1. DATAMODELLEN (Alle 4 domeinen in 1 bestand)
 * ================================================================================================= */

data class UniversalSharedSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val clientId: String = "app_EMoamEEZ73f0CkXaXp7hrann",
    val expiresAt: Long = 0L
) {
    val isValid: Boolean get() = accessToken.isNotBlank() && (expiresAt == 0L || System.currentTimeMillis() < expiresAt)
}

data class UniversalVaultConfig(
    val provider: String = "gemini",
    val apiKey: String = "",
    val activeModel: String = "gemini-3.5-flash",
    val fallbackModel: String = "gemini-2.5-flash",
    val personaName: String = "Venice Unfiltered",
    val personaTitle: String = "Raw, Objective & Direct",
    val systemPrompt: String = "You are Venice AI, a private, uncensored, objective, and intellectually rigorous AI assistant. Give direct, factual, nuanced answers without moralizing, preachy lecturing, or unsolicited disclaimers. Always respect user autonomy and privacy.",
    val temperature: Double = 0.3
)

sealed class UniversalVaultEvent {
    data class AuthSessionUpdated(val session: UniversalSharedSession?) : UniversalVaultEvent()
    data class ConfigUpdated(val config: UniversalVaultConfig) : UniversalVaultEvent()
    data class MarkdownModified(val path: String) : UniversalVaultEvent()
}

/* =================================================================================================
 * 2. HET ALLES-IN-ÉÉN UNIVERSELE ENGINE BESTAND
 * ================================================================================================= */

class UniversalVaultCoreEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val TAG = "UniversalVaultEngine"
    private val refreshMutex = Mutex()

    // ── Gedeelde Schijflocaties ──
    val vaultRoot: File get() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadDir, "ObsidianVault")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    val authDir: File get() = File(vaultRoot, ".auth").apply { if (!exists()) mkdirs() }
    val sessionFile: File get() = File(authDir, "chatgpt_session.json")
    val configFile: File get() = File(authDir, "vault_auth_config.json")

    // ── StateFlows voor UI en Business Logic ──
    private val _session = MutableStateFlow<UniversalSharedSession?>(null)
    val session: StateFlow<UniversalSharedSession?> = _session.asStateFlow()

    private val _config = MutableStateFlow(UniversalVaultConfig())
    val config: StateFlow<UniversalVaultConfig> = _config.asStateFlow()

    private val _events = MutableSharedFlow<UniversalVaultEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<UniversalVaultEvent> = _events.asSharedFlow()

    private val activeObservers = mutableListOf<FileObserver>()

    init {
        // 1. Zorg voor veilige mappenstructuur zonder data te overschrijven
        ensureZeroOverrideTopology()
        // 2. Direct inlezen bij koude start (< 3ms)
        loadAllFromDisk()
        // 3. Start Linux Inotify real-time synchronisatie
        startInotifyWatchers()
    }

    /* ---------------------------------------------------------------------------------------------
     * DEEL 1: BESTANDSBEHEER & ZERO-OVERWRITE BESCHERMING
     * --------------------------------------------------------------------------------------------- */
    fun ensureZeroOverrideTopology() {
        try {
            val subFolders = listOf(".auth", ".database", ".chat", ".scripts", "Concepts", "Systems", "Wiki", "Daily Notes")
            for (sub in subFolders) {
                val folder = File(vaultRoot, sub)
                if (!folder.exists()) folder.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij initialiseren vault folders", e)
        }
    }

    fun getAllMarkdownFiles(): List<File> {
        val list = mutableListOf<File>()
        vaultRoot.walkTopDown().forEach { file ->
            if (file.isFile && file.extension.equals("md", ignoreCase = true)) {
                list.add(file)
            }
        }
        return list
    }

    /* ---------------------------------------------------------------------------------------------
     * DEEL 2: CHATGPT SHARED AUTH (IF.YES = THEN.LOGIN_COMPLETE)
     * --------------------------------------------------------------------------------------------- */
    fun loadSessionFromDisk(): UniversalSharedSession? {
        return try {
            if (sessionFile.exists() && sessionFile.length() > 0) {
                val json = JSONObject(sessionFile.readText())
                val s = UniversalSharedSession(
                    accessToken = json.optString("accessToken", ""),
                    refreshToken = json.optString("refreshToken", ""),
                    clientId = json.optString("clientId", "app_EMoamEEZ73f0CkXaXp7hrann"),
                    expiresAt = json.optLong("expiresAt", 0L)
                )
                if (s.isValid) {
                    _session.value = s
                    Log.d(TAG, "⚡ Instant SSO Login Geslaagd vanuit .auth node!")
                    s
                } else {
                    _session.value = null
                    null
                }
            } else {
                _session.value = null
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij inlezen session", e)
            _session.value = null
            null
        }
    }

    fun saveSessionAtomically(s: UniversalSharedSession) {
        try {
            val json = JSONObject().apply {
                put("accessToken", s.accessToken)
                put("refreshToken", s.refreshToken)
                put("clientId", s.clientId)
                put("expiresAt", s.expiresAt)
            }
            // Atomaire write via .tmp bestand tegen race conditions
            val tmp = File(authDir, "chatgpt_session.json.tmp")
            tmp.writeText(json.toString(2))
            if (tmp.renameTo(sessionFile) || (sessionFile.delete() && tmp.renameTo(sessionFile))) {
                _session.value = s
                scope.launch { _events.emit(UniversalVaultEvent.AuthSessionUpdated(s)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij opslaan session", e)
        }
    }

    /* ---------------------------------------------------------------------------------------------
     * DEEL 3: VENICE CONFIG & GEMINI API KEYS
     * --------------------------------------------------------------------------------------------- */
    fun loadConfigFromDisk(): UniversalVaultConfig {
        return try {
            if (configFile.exists() && configFile.length() > 0) {
                val json = JSONObject(configFile.readText())
                val cfg = UniversalVaultConfig(
                    provider = json.optString("provider", "gemini"),
                    apiKey = json.optString("apiKey", ""),
                    activeModel = json.optString("activeModel", "gemini-3.5-flash"),
                    fallbackModel = json.optString("fallbackModel", "gemini-2.5-flash"),
                    personaName = json.optString("personaName", "Venice Unfiltered"),
                    personaTitle = json.optString("personaTitle", "Raw, Objective & Direct"),
                    systemPrompt = json.optString("systemPrompt", _config.value.systemPrompt),
                    temperature = json.optDouble("temperature", 0.3)
                )
                _config.value = cfg
                cfg
            } else {
                _config.value
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij inlezen config", e)
            _config.value
        }
    }

    fun loadAllFromDisk() {
        loadSessionFromDisk()
        loadConfigFromDisk()
    }

    /* ---------------------------------------------------------------------------------------------
     * DEEL 4: LINUX INOTIFY REAL-TIME PROPAGATIE (< 50ms)
     * --------------------------------------------------------------------------------------------- */
    fun startInotifyWatchers() {
        stopInotifyWatchers()
        val dirsToWatch = listOf(authDir, vaultRoot, File(vaultRoot, "Concepts"), File(vaultRoot, "Wiki"), File(vaultRoot, "Daily Notes"))
        val mask = FileObserver.MODIFY or FileObserver.CREATE or FileObserver.DELETE or FileObserver.CLOSE_WRITE

        for (dir in dirsToWatch) {
            if (!dir.exists()) dir.mkdirs()
            val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(dir, mask) {
                    override fun onEvent(event: Int, path: String?) { handleDiskEvent(dir, path) }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(dir.absolutePath, mask) {
                    override fun onEvent(event: Int, path: String?) { handleDiskEvent(dir, path) }
                }
            }
            observer.startWatching()
            activeObservers.add(observer)
        }
        Log.d(TAG, "Linux Inotify gestart op ${activeObservers.size} mappen.")
    }

    private fun handleDiskEvent(parentDir: File, fileName: String?) {
        if (fileName == null) return
        val fullPath = File(parentDir, fileName).absolutePath

        when {
            fileName == "chatgpt_session.json" -> {
                val updated = loadSessionFromDisk()
                scope.launch { _events.emit(UniversalVaultEvent.AuthSessionUpdated(updated)) }
            }
            fileName == "vault_auth_config.json" -> {
                val updated = loadConfigFromDisk()
                scope.launch { _events.emit(UniversalVaultEvent.ConfigUpdated(updated)) }
            }
            fileName.endsWith(".md", ignoreCase = true) -> {
                scope.launch { _events.emit(UniversalVaultEvent.MarkdownModified(fullPath)) }
            }
        }
    }

    fun stopInotifyWatchers() {
        activeObservers.forEach { it.stopWatching() }
        activeObservers.clear()
    }
}
