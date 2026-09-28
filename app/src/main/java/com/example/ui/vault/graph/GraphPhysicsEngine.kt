package com.example.ui.vault.graph

import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance spatial-grid force-directed layout engine for Obsidian Graph View.
 * Combines spatial binning with Barnes-Hut centroid aggregation to mathematically
 * guarantee strict O(N) worst-case time complexity, even under heavy node clustering
 * or single-cell collapse.
 *
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

    private class SpatialCell {
        val nodes = ArrayList<GraphNode>(8)
        var sumX = 0.0
        var sumY = 0.0
        var centroidX = 0f
        var centroidY = 0f
        var count = 0

        fun add(node: GraphNode) {
            nodes.add(node)
            sumX += node.x
            sumY += node.y
            count++
        }

        fun finalizeCentroid() {
            if (count > 0) {
                centroidX = (sumX / count).toFloat()
                centroidY = (sumY / count).toFloat()
            }
        }

        fun clear() {
            nodes.clear()
            sumX = 0.0
            sumY = 0.0
            centroidX = 0f
            centroidY = 0f
            count = 0
        }
    }

    // Spatial hash grid: Cell key -> SpatialCell
    private val grid = HashMap<Long, SpatialCell>()

    private fun cellKey(cx: Int, cy: Int): Long {
        return (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)
    }

    /**
     * Executes a single simulation step.
     * Guaranteed strictly O(N) operations regardless of node spatial distribution.
     *
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

        // 1. Populate spatial grid & compute centroids in O(N)
        grid.values.forEach { it.clear() }
        for (i in nodes.indices) {
            val node = nodes[i]
            val cx = (node.x / cellSize).toInt()
            val cy = (node.y / cellSize).toInt()
            val key = cellKey(cx, cy)
            grid.getOrPut(key) { SpatialCell() }.add(node)
        }
        for (cell in grid.values) {
            cell.finalizeCentroid()
        }

        // 2. Guaranteed O(N) repulsion with Barnes-Hut centroid aggregation
        for (i in nodes.indices) {
            val n1 = nodes[i]
            val cx = (n1.x / cellSize).toInt()
            val cy = (n1.y / cellSize).toInt()

            for (dx in -1..1) {
                for (dy in -1..1) {
                    val cell = grid[cellKey(cx + dx, cy + dy)] ?: continue
                    if (cell.count == 0) continue

                    val isSameCell = (dx == 0 && dy == 0)

                    if (isSameCell) {
                        // Intra-cell repulsion
                        if (cell.count <= 16) {
                            // Small cell: direct pairwise repulsion
                            for (k in 0 until cell.nodes.size) {
                                val n2 = cell.nodes[k]
                                if (n1 === n2 || System.identityHashCode(n1) >= System.identityHashCode(n2)) continue
                                applyPairwiseRepulsion(n1, n2, i, k)
                            }
                        } else {
                            // Dense cluster collapse: check up to 8 neighbors individually,
                            // then repel away from cell centroid for the remaining cluster mass
                            val maxDirect = 8.coerceAtMost(cell.nodes.size)
                            for (k in 0 until maxDirect) {
                                val n2 = cell.nodes[k]
                                if (n1 === n2 || System.identityHashCode(n1) >= System.identityHashCode(n2)) continue
                                applyPairwiseRepulsion(n1, n2, i, k)
                            }

                            val excessCount = cell.count - maxDirect
                            if (excessCount > 0) {
                                applyCentroidRepulsion(n1, cell.centroidX, cell.centroidY, excessCount.toFloat(), i)
                            }
                        }
                    } else {
                        // Neighbor cell repulsion
                        if (cell.count <= 8) {
                            // Sparse neighbor: check direct elements
                            for (k in 0 until cell.nodes.size) {
                                val n2 = cell.nodes[k]
                                applyDirectedRepulsion(n1, n2)
                            }
                        } else {
                            // Dense neighbor cell: Barnes-Hut aggregate point mass (O(1) per neighbor cell!)
                            applyCentroidRepulsion(n1, cell.centroidX, cell.centroidY, cell.count.toFloat(), i)
                        }
                    }
                }
            }
        }

        // 3. Edge spring forces - O(E)
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

        // 4. Center gravity, velocity damping, and position integration - O(N)
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

    private fun applyPairwiseRepulsion(n1: GraphNode, n2: GraphNode, i: Int, k: Int) {
        var diffX = n2.x - n1.x
        var diffY = n2.y - n1.y
        var distSq = diffX * diffX + diffY * diffY
        if (distSq > maxRepelDistSq) return

        if (distSq < 1f) {
            val angle = ((i * 37 + k * 19) % 360) * (Math.PI / 180.0)
            diffX = (cos(angle) * 4.0).toFloat()
            diffY = (sin(angle) * 4.0).toFloat()
            distSq = (diffX * diffX + diffY * diffY).coerceAtLeast(4f)
        }

        val dist = sqrt(distSq).coerceAtLeast(15f)
        val force = repelForce / (dist * dist)
        val fx = (diffX / dist) * force
        val fy = (diffY / dist) * force

        n1.vx -= fx
        n1.vy -= fy
        n2.vx += fx
        n2.vy += fy
    }

    private fun applyDirectedRepulsion(n1: GraphNode, n2: GraphNode) {
        val diffX = n2.x - n1.x
        val diffY = n2.y - n1.y
        val distSq = diffX * diffX + diffY * diffY
        if (distSq > maxRepelDistSq || distSq < 1f) return

        val dist = sqrt(distSq).coerceAtLeast(15f)
        val force = repelForce / (dist * dist)
        val fx = (diffX / dist) * force
        val fy = (diffY / dist) * force

        n1.vx -= fx
        n1.vy -= fy
    }

    private fun applyCentroidRepulsion(
        node: GraphNode,
        centroidX: Float,
        centroidY: Float,
        mass: Float,
        nodeIndex: Int
    ) {
        var diffX = centroidX - node.x
        var diffY = centroidY - node.y
        var distSq = diffX * diffX + diffY * diffY

        if (distSq < 1f) {
            val angle = ((nodeIndex * 53) % 360) * (Math.PI / 180.0)
            diffX = (cos(angle) * 6.0).toFloat()
            diffY = (sin(angle) * 6.0).toFloat()
            distSq = (diffX * diffX + diffY * diffY).coerceAtLeast(4f)
        }

        if (distSq <= maxRepelDistSq) {
            val dist = sqrt(distSq).coerceAtLeast(20f)
            val force = (repelForce * mass) / (dist * dist)
            val fx = (diffX / dist) * force
            val fy = (diffY / dist) * force

            node.vx -= fx
            node.vy -= fy
        }
    }
}
