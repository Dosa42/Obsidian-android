package com.example.ui.vault.graph

import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import kotlin.math.sqrt

/**
 * High-performance spatial-grid force-directed layout engine for Obsidian Graph View.
 * Uses spatial binning to achieve O(N) repulsion calculations instead of O(N^2) pairwise comparisons.
 * Supports isolated subgraph physics for Local Graph mode.
 */
class GraphPhysicsEngine(
    var centerForce: Float = 0.008f,
    var repelForce: Float = 2400f,
    var linkForce: Float = 0.04f,
    var linkDistance: Float = 130f,
    var damping: Float = 0.85f,
    var maxRepelDistance: Float = 420f
) {
    private val maxRepelDistSq = maxRepelDistance * maxRepelDistance
    private val cellSize = maxRepelDistance

    // Spatial hash grid: Cell key -> list of nodes in cell
    private val grid = HashMap<Long, MutableList<GraphNode>>()

    private fun cellKey(cx: Int, cy: Int): Long {
        return (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Executes a single simulation step.
     * @param nodes The active nodes in the simulation (e.g. filtered nodes or local graph subgraph)
     * @param edges The edges connecting the active nodes
     * @param draggedNodeId The ID of the node currently dragged by the user, if any
     * @return Total kinetic movement across all nodes in this step
     */
    fun step(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        draggedNodeId: String?
    ): Float {
        if (nodes.isEmpty()) return 0f

        val nodeMap = nodes.associateBy { it.id }

        // 1. Populate spatial grid for O(N) neighbor repulsion
        grid.clear()
        for (i in nodes.indices) {
            val node = nodes[i]
            val cx = (node.x / cellSize).toInt()
            val cy = (node.y / cellSize).toInt()
            val key = cellKey(cx, cy)
            grid.getOrPut(key) { ArrayList(8) }.add(node)
        }

        // 2. Repulsion using 9-cell spatial neighborhood
        for (i in nodes.indices) {
            val n1 = nodes[i]
            val cx = (n1.x / cellSize).toInt()
            val cy = (n1.y / cellSize).toInt()

            for (dx in -1..1) {
                for (dy in -1..1) {
                    val neighborList = grid[cellKey(cx + dx, cy + dy)] ?: continue
                    for (k in neighborList.indices) {
                        val n2 = neighborList[k]
                        // Avoid self-interaction and double counting by pointer or index comparison
                        if (n1 === n2 || System.identityHashCode(n1) >= System.identityHashCode(n2)) continue

                        val diffX = n2.x - n1.x
                        val diffY = n2.y - n1.y
                        val distSq = diffX * diffX + diffY * diffY
                        if (distSq > maxRepelDistSq) continue

                        val dist = sqrt(distSq).coerceAtLeast(15f)
                        val force = repelForce / (dist * dist)
                        val fx = (diffX / dist) * force
                        val fy = (diffY / dist) * force

                        n1.vx -= fx
                        n1.vy -= fy
                        n2.vx += fx
                        n2.vy += fy
                    }
                }
            }
        }

        // 3. Edge spring forces
        for (edge in edges) {
            val s = nodeMap[edge.sourceId] ?: continue
            val t = nodeMap[edge.targetId] ?: continue

            val diffX = t.x - s.x
            val diffY = t.y - s.y
            val dist = sqrt(diffX * diffX + diffY * diffY).coerceAtLeast(1f)
            val displacement = dist - linkDistance
            val force = displacement * linkForce
            val fx = (diffX / dist) * force
            val fy = (diffY / dist) * force

            s.vx += fx
            s.vy += fy
            t.vx -= fx
            t.vy -= fy
        }

        // 4. Center gravity, velocity damping, and position integration
        var totalMovement = 0f
        for (i in nodes.indices) {
            val node = nodes[i]
            if (node.id != draggedNodeId && !node.isPinned) {
                node.vx -= node.x * centerForce
                node.vy -= node.y * centerForce
                node.vx *= damping
                node.vy *= damping
                node.x += node.vx
                node.y += node.vy
                totalMovement += sqrt(node.vx * node.vx + node.vy * node.vy)
            } else {
                node.vx = 0f
                node.vy = 0f
            }
        }

        return totalMovement
    }
}
