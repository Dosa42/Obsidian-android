package com.example.data.local

import androidx.room.*
import com.example.data.model.VaultNote
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<VaultNote>>

    @Query("SELECT * FROM vault_notes ORDER BY lastModified DESC")
    suspend fun getAllNotesSync(): List<VaultNote>

    @Query("SELECT * FROM vault_notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: Long): Flow<VaultNote?>

    @Query("SELECT * FROM vault_notes WHERE path = :path LIMIT 1")
    suspend fun getNoteByPath(path: String): VaultNote?

    @Query("SELECT * FROM vault_notes WHERE title = :title LIMIT 1")
    suspend fun getNoteByTitle(title: String): VaultNote?

    @Query("SELECT * FROM vault_notes WHERE isBookmarked = 1 ORDER BY title ASC")
    fun getBookmarkedNotes(): Flow<List<VaultNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: VaultNote): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<VaultNote>)

    @Update
    suspend fun updateNote(note: VaultNote)

    @Delete
    suspend fun deleteNote(note: VaultNote)

    @Query("DELETE FROM vault_notes WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM vault_notes WHERE path NOT IN (:activePaths)")
    suspend fun deleteRemovedPaths(activePaths: List<String>)

    @Query("SELECT * FROM vault_notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    suspend fun searchNotes(query: String): List<VaultNote>
}
