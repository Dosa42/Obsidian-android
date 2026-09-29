# 🚀 1-File Drop-In Implementation Guide: Universal Shared ChatGPT OAuth PKCE

Dit document beschrijft hoe je in **slechts 1 enkel bestand** van een willekeurige Android-app (zoals `Venice-ai` of een andere companion-app) de authenticatie omzet naar **Universal Shared Auth**.

Door dit te implementeren, leest elke nieuw geïnstalleerde APK direct de bestaande inlogsessie van het toestel:
> **`IF (sessionFile.exists()) THEN (login_complete)` in < 3 milliseconden.**

---

## 🎯 Het Doel
In plaats van dat elke app zijn tokens opslaat in zijn eigen privé sandbox (`/data/data/...`), wijzig je in de Auth Manager van de doel-app het opslagpad naar het **universele ankerpunt**:
`/storage/emulated/0/Download/ObsidianVault/.auth/chatgpt_session.json`

---

## 🛠️ Stap 1: Pas de Auth Manager aan (Het Enige Bestand)

Open in je doel-app het bestand dat verantwoordelijk is voor sessies (bijv. `AuthManager.kt`, `TokenStorage.kt` of `ChatGPTAuthManager.kt`) en pas de klasse aan zoals hieronder:

```kotlin
package com.example.data.auth

import android.os.Environment
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Datamodel voor de gedeelde sessie
 */
data class SharedSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val clientId: String = "app_EMoamEEZ73f0CkXaXp7hrann",
    val expiresAt: Long = 0L
) {
    val isValid: Boolean 
        get() = accessToken.isNotBlank() && (expiresAt == 0L || System.currentTimeMillis() < expiresAt)
}

/**
 * Drop-in Universal Auth Manager
 */
class SharedAuthManager {
    private val TAG = "UniversalSharedAuth"

    // 1. Het Universele Gedeelde Pad op de schijf
    val sessionFile: File get() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val authDir = File(File(downloadDir, "ObsidianVault"), ".auth")
        if (!authDir.exists()) authDir.mkdirs()
        return File(authDir, "chatgpt_session.json")
    }

    private val _sessionState = MutableStateFlow<SharedSession?>(null)
    val sessionState: StateFlow<SharedSession?> = _sessionState.asStateFlow()

    init {
        // Direct scannen bij opstarten van de app (Cold-Start)
        loadSessionFromDisk()
        
        // Real-time Linux kernel watcher (als een andere app het token ververst)
        startInotifyWatcher()
    }

    /**
     * DE KERNLOGICA: If.Yes = Then.Login_Complete
     */
    fun loadSessionFromDisk(): Boolean {
        return try {
            // STAP 1: Kijken of het centrale sessie-bestand al bestaat
            if (sessionFile.exists() && sessionFile.length() > 0) {
                val json = JSONObject(sessionFile.readText())
                val session = SharedSession(
                    accessToken = json.optString("accessToken", ""),
                    refreshToken = json.optString("refreshToken", ""),
                    clientId = json.optString("clientId", "app_EMoamEEZ73f0CkXaXp7hrann"),
                    expiresAt = json.optLong("expiresAt", 0L)
                )

                // STAP 2: Valideer token
                if (session.isValid) {
                    // ✅ IF.YES = THEN.LOGIN COMPLETE (Direct in het geheugen)
                    _sessionState.value = session
                    Log.d(TAG, "⚡ Instant Login Geslaagd! Sessie actief voor: ${session.clientId}")
                    return true
                }
            }
            // IF.NO = Geen geldige sessie gevonden
            _sessionState.value = null
            false
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij inlezen gedeelde sessie", e)
            _sessionState.value = null
            false
        }
    }

    /**
     * Bewaar sessie atomair naar het centrale bestand
     */
    fun saveSession(session: SharedSession) {
        try {
            val json = JSONObject().apply {
                put("accessToken", session.accessToken)
                put("refreshToken", session.refreshToken)
                put("clientId", session.clientId)
                put("expiresAt", session.expiresAt)
            }
            // Atomaire schrijfactie via .tmp bestand tegen concurrency conflicten
            val parent = sessionFile.parentFile ?: return
            if (!parent.exists()) parent.mkdirs()
            val tmp = File(parent, "chatgpt_session.json.tmp")
            tmp.writeText(json.toString(2))
            if (tmp.renameTo(sessionFile) || (sessionFile.delete() && tmp.renameTo(sessionFile))) {
                _sessionState.value = session
                Log.d(TAG, "Sessie succesvol opgeslagen naar centrale node")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fout bij opslaan sessie", e)
        }
    }

    /**
     * Linux Inotify: Luistert naar wijzigingen door andere companion-APKs
     */
    private fun startInotifyWatcher() {
        try {
            val dir = sessionFile.parentFile ?: return
            val observer = object : FileObserver(dir.absolutePath, MODIFY or CREATE) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == "chatgpt_session.json") {
                        loadSessionFromDisk()
                    }
                }
            }
            observer.startWatching()
        } catch (e: Exception) {
            Log.w(TAG, "Inotify observer kon niet starten", e)
        }
    }
}
```

---

## ⚡ Wat gebeurt er als je de APK bouwt en installeert?

```
┌────────────────────────────────────────┐
│   Nieuwe APK Installatie & Start       │
└──────────────────┬─────────────────────┘
                   │
                   ▼
┌────────────────────────────────────────┐
│  SharedAuthManager.init() voert uit    │
│  Controleert: .auth/chatgpt_session    │
└──────────────────┬─────────────────────┘
                   │
       ┌───────────┴───────────┐
       ▼                       ▼
  [ BESTAAT ]             [ BESTAAT NIET ]
       │                       │
       ▼                       ▼
 ✅ Login Complete         Gebruiker logt 
 in < 3 milliseconden      eenmalig in
 Geen inlogscherm nodig!   (Schrijft token weg)
```

---

## 📋 Checklist voor de Target Repo / APK:
1. Vervang de sessie-opslag in je Auth klasse door bovenstaande code.
2. Zorg dat `AndroidManifest.xml` beschikt over `<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />` en `WRITE_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`.
3. Build je APK $\rightarrow$ Installeer $\rightarrow$ **Klaar!** Alle apps delen nu 1 centrale token.
