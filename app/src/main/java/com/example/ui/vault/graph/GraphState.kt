package com.example.ui.vault.graph

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

data class GraphForcesConfig(
    val centerForce: Float = 0.008f,
    val repelForce: Float = 2400f,
    val linkForce: Float = 0.04f,
    val linkDistance: Float = 130f,
    val damping: Float = 0.85f,
    val isSimulating: Boolean = true
)

data class GraphFilterConfig(
    val searchQuery: String = "",
    val showExistingNotes: Boolean = true,
    val showUnresolvedNotes: Boolean = true,
    val showOrphans: Boolean = true,
    val showTags: Boolean = true,
    val showAttachments: Boolean = true
)

data class GraphDisplayConfig(
    val showArrows: Boolean = true,
    val nodeSizeMultiplier: Float = 1.0f,
    val lineThickness: Float = 1.0f,
    val textFadeThreshold: Float = 0.65f,
    val isLocalGraph: Boolean = false,
    val localGraphDepth: Int = 1,
    val isEcoMode: Boolean = false
)

data class GraphColorGroup(
    val id: String,
    val label: String,
    val query: String, // e.g. "folder:Concepts" or "tag:project" or "unresolved"
    val color: Color
)

object DefaultGraphColorGroups {
    fun getDefaults(): List<GraphColorGroup> = listOf(
        GraphColorGroup("1", "Concepts Folder", "folder:Concepts", ObsidianPurple),
        GraphColorGroup("2", "Systems Folder", "folder:Systems", ObsidianTeal),
        GraphColorGroup("3", "Wiki Folder", "folder:Wiki", ObsidianYellow),
        GraphColorGroup("4", "Daily Notes", "folder:Daily Notes", ObsidianGreen),
        GraphColorGroup("5", "Unresolved Links", "type:unresolved", Color(0xFFF59E0B)), // Amber
        GraphColorGroup("6", "Tags", "type:tag", ObsidianCyan)
    )
}
