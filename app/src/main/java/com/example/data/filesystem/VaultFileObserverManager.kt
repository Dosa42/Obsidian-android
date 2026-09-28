package com.example.data.filesystem

import android.os.Build
import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

sealed class VaultFileSystemEvent(val path: String) {
    class ConfigModified(path: String) : VaultFileSystemEvent(path)
    class ScriptsModified(path: String) : VaultFileSystemEvent(path)
    class NoteModified(path: String) : VaultFileSystemEvent(path)
    class GenericModified(path: String) : VaultFileSystemEvent(path)
}

class VaultFileObserverManager(
    private val rootVaultDir: File,
    private val coroutineScope: CoroutineScope
) {
    private val TAG = "VaultFileObserver"

    private val _eventsFlow = MutableSharedFlow<VaultFileSystemEvent>(extraBufferCapacity = 64)
    val eventsFlow: SharedFlow<VaultFileSystemEvent> = _eventsFlow.asSharedFlow()

    private val activeObservers = mutableListOf<FileObserver>()
    private var debounceJob: Job? = null
    private var isRunning = false

    fun startWatching() {
        if (isRunning) return
        stopWatching()
        isRunning = true

        try {
            val watchedDirs = listOf(
                rootVaultDir,
                File(rootVaultDir, ".auth"),
                File(rootVaultDir, ".config"),
                File(rootVaultDir, ".scripts"),
                File(rootVaultDir, "Concepts"),
                File(rootVaultDir, "Systems"),
                File(rootVaultDir, "Wiki"),
                File(rootVaultDir, "Daily Notes")
            )

            for (dir in watchedDirs) {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                registerDirectoryObserver(dir)
            }
            Log.d(TAG, "Started FileObserver watchers on ${activeObservers.size} directories in ${rootVaultDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting file observers", e)
        }
    }

    private fun registerDirectoryObserver(dir: File) {
        val mask = FileObserver.MODIFY or FileObserver.CLOSE_WRITE or FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_TO or FileObserver.MOVED_FROM

        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(dir, path, event)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    handleFileEvent(dir, path, event)
                }
            }
        }

        observer.startWatching()
        activeObservers.add(observer)
    }

    private fun handleFileEvent(parentDir: File, fileName: String?, event: Int) {
        if (fileName == null) return
        // Ignore sqlite db lock/temp files, wal/shm, and editor temp files
        if (fileName.endsWith(".db-journal") || fileName.endsWith(".db-wal") || fileName.endsWith(".db-shm") || fileName.startsWith(".~")) {
            return
        }

        val fullPath = File(parentDir, fileName).absolutePath

        debounceJob?.cancel()
        debounceJob = coroutineScope.launch(Dispatchers.Default) {
            delay(300) // 300ms debounce to batch consecutive write operations

            val eventType = when {
                parentDir.name == ".auth" || fileName.contains("vault_auth_config.json") -> {
                    VaultFileSystemEvent.ConfigModified(fullPath)
                }
                parentDir.name == ".scripts" || fileName.endsWith(".json") || fileName.endsWith(".js") -> {
                    VaultFileSystemEvent.ScriptsModified(fullPath)
                }
                fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true) -> {
                    VaultFileSystemEvent.NoteModified(fullPath)
                }
                else -> {
                    VaultFileSystemEvent.GenericModified(fullPath)
                }
            }

            Log.d(TAG, "Hot-Reload triggered via FileObserver / Inotify: ${eventType::class.simpleName} -> $fullPath")
            _eventsFlow.emit(eventType)
        }
    }

    fun stopWatching() {
        for (obs in activeObservers) {
            try {
                obs.stopWatching()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping observer", e)
            }
        }
        activeObservers.clear()
        isRunning = false
    }
}
