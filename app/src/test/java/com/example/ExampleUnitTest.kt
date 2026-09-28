package com.example

import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.ui.vault.graph.GraphPhysicsEngine
import com.example.ui.vault.graph.ObsidianGraphQueryParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testObsidianGraphQueryParserBasic() {
        val note = GraphNode(
            id = "Concepts/Knowledge Graphs.md",
            title = "Knowledge Graphs",
            folder = "Concepts",
            degree = 3,
            path = "Concepts/Knowledge Graphs.md",
            nodeType = GraphNodeType.NOTE,
            tags = listOf("#concept", "#graph")
        )

        // Matching file
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "file:Knowledge"))
        assertFalse(ObsidianGraphQueryParser.matches(note, null, "file:Missing"))

        // Matching path / folder
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "path:Concepts"))
        assertFalse(ObsidianGraphQueryParser.matches(note, null, "path:Daily"))

        // Matching tag
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "tag:#concept"))
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "tag:graph"))
        assertFalse(ObsidianGraphQueryParser.matches(note, null, "tag:systems"))

        // Negation
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "-path:Daily"))
        assertFalse(ObsidianGraphQueryParser.matches(note, null, "-path:Concepts"))

        // Multiple clauses (AND)
        assertTrue(ObsidianGraphQueryParser.matches(note, null, "path:Concepts tag:#concept -file:Draft"))
        assertFalse(ObsidianGraphQueryParser.matches(note, null, "path:Concepts tag:#missing"))
    }

    @Test
    fun testGraphPhysicsEngine() {
        val engine = GraphPhysicsEngine(
            centerForce = 0.01f,
            repelForce = 1000f,
            linkForce = 0.05f,
            linkDistance = 100f,
            damping = 0.8f
        )

        val n1 = GraphNode(id = "1", title = "N1", folder = "F", x = 0f, y = 0f)
        val n2 = GraphNode(id = "2", title = "N2", folder = "F", x = 10f, y = 0f)
        val edge = GraphEdge(sourceId = "1", targetId = "2", isResolved = true)

        val movement = engine.step(listOf(n1, n2), listOf(edge), draggedNodeId = null)
        assertTrue(movement > 0f)
        // Check that nodes repelled from each other
        assertTrue(n1.x < 0f)
        assertTrue(n2.x > 10f)
    }
}
