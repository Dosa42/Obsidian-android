package com.example.ui.vault.graph

import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType

/**
 * Obsidian Graph View Query Engine.
 * Supports standard Obsidian graph search syntax:
 * - `file:(term)` or `file:term` - Matches note title or filename
 * - `path:(term)` or `path:term` or `folder:term` - Matches file path / folder
 * - `tag:#term` or `tag:term` or `#term` - Matches tags
 * - `line:(term)` or `content:term` - Matches note content
 * - `type:note|unresolved|tag|attachment` - Matches node type
 * - Negation `-` prefix: e.g. `-tag:#archived`, `-path:Daily`, `-file:Test`
 * - Bare search terms: matches across title, path, folder, or tags
 * - Multiple space-separated clauses are combined with logical AND.
 */
object ObsidianGraphQueryParser {

    data class QueryClause(
        val isNegated: Boolean,
        val field: String?, // "file", "path", "folder", "tag", "line", "content", "type", or null
        val term: String
    )

    fun parse(rawQuery: String): List<QueryClause> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()

        val clauses = mutableListOf<QueryClause>()
        val regex = Regex("""(-?)(file:|path:|folder:|tag:|content:|line:|type:)?("([^"]+)"|\(([^)]+)\)|([^\s]+))""")
        val matches = regex.findAll(trimmed)

        for (m in matches) {
            val isNegated = m.groupValues[1] == "-"
            val field = m.groupValues[2].removeSuffix(":").lowercase().takeIf { it.isNotEmpty() }
            val rawTerm = m.groupValues[4].ifEmpty {
                m.groupValues[5].ifEmpty {
                    m.groupValues[6]
                }
            }
            if (rawTerm.isNotBlank()) {
                val cleanTerm = rawTerm.trim().lowercase()
                // If term starts with # and field was empty, treat as tag:
                if (field == null && cleanTerm.startsWith("#")) {
                    clauses.add(
                        QueryClause(
                            isNegated = isNegated,
                            field = "tag",
                            term = cleanTerm.removePrefix("#")
                        )
                    )
                } else {
                    clauses.add(
                        QueryClause(
                            isNegated = isNegated,
                            field = field,
                            term = cleanTerm.removePrefix("#")
                        )
                    )
                }
            }
        }

        return clauses
    }

    fun matches(node: GraphNode, noteContent: String?, query: String): Boolean {
        if (query.isBlank()) return true
        val clauses = parse(query)
        if (clauses.isEmpty()) return true

        for (clause in clauses) {
            val clauseMatched = matchSingleClause(node, noteContent, clause)
            if (clause.isNegated) {
                if (clauseMatched) return false // Negated clause matched -> overall false
            } else {
                if (!clauseMatched) return false // Positive clause didn't match -> overall false
            }
        }
        return true
    }

    private fun matchSingleClause(node: GraphNode, noteContent: String?, clause: QueryClause): Boolean {
        val term = clause.term
        return when (clause.field) {
            "file" -> {
                node.title.lowercase().contains(term) || node.path.lowercase().contains(term)
            }
            "path", "folder" -> {
                node.folder.lowercase().contains(term) || node.path.lowercase().contains(term)
            }
            "tag" -> {
                node.tags.any { it.removePrefix("#").lowercase().contains(term) } ||
                        (node.nodeType == GraphNodeType.TAG && node.title.removePrefix("#").lowercase().contains(term))
            }
            "content", "line" -> {
                noteContent?.lowercase()?.contains(term) == true
            }
            "type" -> {
                when (term) {
                    "note" -> node.nodeType == GraphNodeType.NOTE
                    "unresolved" -> node.nodeType == GraphNodeType.UNRESOLVED
                    "tag" -> node.nodeType == GraphNodeType.TAG
                    "attachment" -> node.nodeType == GraphNodeType.ATTACHMENT
                    else -> node.nodeType.name.lowercase() == term
                }
            }
            else -> {
                // Bare term matches across title, path, folder, tags, and unresolved name
                node.title.lowercase().contains(term) ||
                        node.folder.lowercase().contains(term) ||
                        node.path.lowercase().contains(term) ||
                        node.tags.any { it.lowercase().contains(term) } ||
                        (noteContent?.lowercase()?.contains(term) == true)
            }
        }
    }
}
