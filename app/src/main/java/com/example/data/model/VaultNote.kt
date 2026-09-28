package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_notes")
data class VaultNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String, // e.g. "Concepts/Knowledge Graphs.md"
    val title: String, // e.g. "Knowledge Graphs"
    val folder: String = "Root", // e.g. "Concepts"
    val content: String = "",
    val lastModified: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0L,
    val tags: List<String> = emptyList(),
    val outlinks: List<String> = emptyList(), // extracted [[wikilinks]]
    val isBookmarked: Boolean = false
)

data class FolderNode(
    val name: String,
    val path: String,
    val subfolders: MutableList<FolderNode> = mutableListOf(),
    val notes: MutableList<VaultNote> = mutableListOf()
)

enum class GraphNodeType {
    NOTE,
    UNRESOLVED,
    TAG,
    ATTACHMENT
}

data class GraphNode(
    val id: String,
    val title: String,
    val folder: String,
    val degree: Int = 0,
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val path: String = "",
    val nodeType: GraphNodeType = GraphNodeType.NOTE,
    val tags: List<String> = emptyList(),
    var isPinned: Boolean = false
)

data class GraphEdge(
    val sourceId: String,
    val targetId: String,
    val isResolved: Boolean = true
)

data class BacklinkItem(
    val note: VaultNote,
    val previewSnippet: String,
    val isExplicitLink: Boolean // true if [[wikilink]], false if unlinked mention
)
