package com.example.data.local

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.VaultNote
import java.io.File

@Database(entities = [VaultNote::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        private const val TAG = "VaultDatabase"

        @Volatile
        private var INSTANCE: VaultDatabase? = null

        fun getDatabasePath(context: Context): File {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val vaultDir = File(downloadDir, "ObsidianVault")
            val dbDir = File(vaultDir, ".database")
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            return File(dbDir, "vault_storage.db")
        }

        fun getDatabase(context: Context): VaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbFile = getDatabasePath(context)
                try {
                    dbFile.parentFile?.mkdirs()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed ensuring database directory ${dbFile.parent}", e)
                }

                val dbPath = try {
                    if (dbFile.parentFile?.canWrite() == true || dbFile.parentFile?.exists() == true) {
                        dbFile.absolutePath
                    } else {
                        "obsidian_vault.db"
                    }
                } catch (e: Exception) {
                    "obsidian_vault.db"
                }

                Log.d(TAG, "Opening Room database at: $dbPath")

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VaultDatabase::class.java,
                    dbPath
                ).fallbackToDestructiveMigration(true).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
