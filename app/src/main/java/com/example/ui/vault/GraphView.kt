package com.example.ui.vault

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.adaptive.HardwareContextState
import com.example.data.model.GraphEdge
import com.example.data.model.GraphNode
import com.example.data.model.GraphNodeType
import com.example.data.model.VaultNote
import com.example.data.repository.VaultGraphData
import com.example.ui.theme.*
import com.example.ui.vault.graph.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GraphView(
    graphData: VaultGraphData,
    allNotes: List<VaultNote>,
    activeNote: VaultNote? = null,
    hardwareState: HardwareContextState? = null,
    onOpenNote: (VaultNote) -> Unit,
    onOpenNoteByTitle: (String) -> Unit = {},
    onSavePositions: (Map<String, Pair<Float, Float>>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Configuration states
    var filters by remember { mutableStateOf(GraphFilterConfig()) }
    var forces by remember { mutableStateOf(GraphForcesConfig()) }
    var display by remember {
        mutableStateOf(
            GraphDisplayConfig(
                isEcoMode = hardwareState?.enableLiveGraphPhysics == false
            )
        )
    }
    var colorGroups by remember { mutableStateOf(DefaultGraphColorGroups.getDefaults()) }

    // Update eco mode if hardware state changes
    LaunchedEffect(hardwareState?.enableLiveGraphPhysics) {
        if (hardwareState != null) {
            display = display.copy(isEcoMode = !hardwareState.enableLiveGraphPhysics)
        }
    }

    // Local mutable state for physics simulation positions
    val localNodes = remember(graphData.nodes) {
        graphData.nodes.map {
            GraphNode(
                id = it.id,
                title = it.title,
                folder = it.folder,
                degree = it.degree,
                x = it.x,
                y = it.y,
                vx = 0f,
                vy = 0f,
                path = it.path,
                nodeType = it.nodeType,
                tags = it.tags,
                isPinned = false
            )
        }.toMutableStateList()
    }

    // Tick counter to trigger smooth observable recomposition on Canvas
    var simTick by remember { mutableIntStateOf(0) }

    // Content map for full Obsidian query filtering and content matching
    val noteContentMap = remember(allNotes) { allNotes.associate { it.path to it.content } }

    // BFS Reachable Subgraph for Local Graph isolation
    val localReachableIds = remember(display.isLocalGraph, display.localGraphDepth, selectedNodeId, activeNote, graphData.edges) {
        if (!display.isLocalGraph) {
            null
        } else {
            val focusId = selectedNodeId
                ?: activeNote?.path
                ?: activeNote?.title
                ?: graphData.nodes.firstOrNull()?.id
            if (focusId == null) {
                null
            } else {
                val reachableIds = mutableSetOf<String>()
                reachableIds.add(focusId)

                val queue = ArrayDeque<Pair<String, Int>>()
                queue.add(Pair(focusId, 0))

                val adjacency = mutableMapOf<String, MutableSet<String>>()
                for (edge in graphData.edges) {
                    adjacency.getOrPut(edge.sourceId) { mutableSetOf() }.add(edge.targetId)
                    adjacency.getOrPut(edge.targetId) { mutableSetOf() }.add(edge.sourceId)
                }

                while (queue.isNotEmpty()) {
                    val (current, currentDepth) = queue.poll()
                    if (currentDepth < display.localGraphDepth) {
                        val neighbors = adjacency[current] ?: emptySet()
                        for (neighbor in neighbors) {
                            if (reachableIds.add(neighbor)) {
                                queue.add(Pair(neighbor, currentDepth + 1))
                            }
                        }
                    }
                }
                reachableIds
            }
        }
    }

    // Force-directed layout physics simulation loop running on background coroutine
    LaunchedEffect(
        forces.isSimulating,
        forces.centerForce,
        forces.repelForce,
        forces.linkForce,
        forces.linkDistance,
        forces.damping,
        graphData.edges,
        display.isEcoMode,
        display.isLocalGraph,
        localReachableIds
    ) {
        if (!forces.isSimulating) return@LaunchedEffect

        val frameDelay = if (display.isEcoMode) 33L else 16L // 30fps on eco mode, 60fps on normal
        var consecutiveQuietSteps = 0

        withContext(Dispatchers.Default) {
            val physicsEngine = GraphPhysicsEngine(
                centerForce = forces.centerForce,
                repelForce = forces.repelForce,
                linkForce = forces.linkForce,
                linkDistance = forces.linkDistance,
                damping = forces.damping,
                maxRepelDistance = if (display.isEcoMode) 320f else 420f
            )

            while (isActive && forces.isSimulating) {
                // When in Local Graph mode, simulate ONLY the active reachable neighborhood!
                val simNodes = if (display.isLocalGraph && localReachableIds != null) {
                    localNodes.filter { localReachableIds.contains(it.id) }
                } else {
                    localNodes
                }

                val simNodeIds = simNodes.map { it.id }.toSet()
                val simEdges = graphData.edges.filter {
                    simNodeIds.contains(it.sourceId) && simNodeIds.contains(it.targetId)
                }

                // Execute O(N) spatial grid simulation step
                val totalMovement = physicsEngine.step(simNodes, simEdges, draggedNodeId)

                // Dispatch recomposition tick to UI thread
                withContext(Dispatchers.Main) {
                    simTick++
                }

                // Settle detection & durable position persistence
                if (draggedNodeId == null && totalMovement < 0.25f) {
                    consecutiveQuietSteps++
                    // Persist settled positions to disk upon reaching rest
                    if (consecutiveQuietSteps == 30) {
                        val snapshot = localNodes.associate { it.id to Pair(it.x, it.y) }
                        withContext(Dispatchers.Main) {
                            onSavePositions(snapshot)
                        }
                    }
                    if (consecutiveQuietSteps > 30) {
                        delay(200L) // Idle sleep until user drag or configuration change
                        continue
                    }
                } else {
                    consecutiveQuietSteps = 0
                }

                delay(frameDelay)
            }
        }
    }

    // Save node positions immediately after user drag interaction finishes
    LaunchedEffect(draggedNodeId) {
        if (draggedNodeId == null && localNodes.isNotEmpty()) {
            val snapshot = localNodes.associate { it.id to Pair(it.x, it.y) }
            onSavePositions(snapshot)
        }
    }

    // Calculate Active & Filtered Nodes using full Obsidian search query syntax
    val visibleNodes = remember(localNodes, filters, display, selectedNodeId, activeNote, localReachableIds, simTick) {
        // Base filtering by node type
        var nodes = localNodes.filter { node ->
            when (node.nodeType) {
                GraphNodeType.NOTE -> filters.showExistingNotes
                GraphNodeType.UNRESOLVED -> filters.showUnresolvedNotes
                GraphNodeType.TAG -> filters.showTags
                GraphNodeType.ATTACHMENT -> filters.showAttachments
            }
        }

        // Orphan filter
        if (!filters.showOrphans) {
            nodes = nodes.filter { it.degree > 0 }
        }

        // Full Obsidian search query filter (supports file:, path:, tag:, content:, negation -, bare words)
        if (filters.searchQuery.isNotBlank()) {
            nodes = nodes.filter { node ->
                ObsidianGraphQueryParser.matches(node, noteContentMap[node.path], filters.searchQuery)
            }
        }

        // Local Graph BFS Neighborhood filter
        if (display.isLocalGraph && localReachableIds != null) {
            nodes = nodes.filter { localReachableIds.contains(it.id) }
        }

        nodes
    }

    val visibleNodeIds = remember(visibleNodes) { visibleNodes.map { it.id }.toSet() }

    // Visible Edges
    val visibleEdges = remember(graphData.edges, visibleNodeIds) {
        graphData.edges.filter {
            visibleNodeIds.contains(it.sourceId) && visibleNodeIds.contains(it.targetId)
        }
    }

    // Neighbors of selected node for focus highlighting
    val selectedConnectedIds = remember(selectedNodeId, visibleEdges) {
        if (selectedNodeId == null) {
            emptySet()
        } else {
            val set = mutableSetOf<String>()
            set.add(selectedNodeId!!)
            visibleEdges.forEach { edge ->
                if (edge.sourceId == selectedNodeId) set.add(edge.targetId)
                if (edge.targetId == selectedNodeId) set.add(edge.sourceId)
            }
            set
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.25f, 4.0f)
        panOffset += offsetChange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .transformable(state = transformState)
                .pointerInput(visibleNodes, scale, panOffset) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val worldX = (offset.x - centerX - panOffset.x) / scale
                            val worldY = (offset.y - centerY - panOffset.y) / scale

                            val hit = visibleNodes.firstOrNull { node ->
                                val r = (14f + node.degree * 2.5f) * display.nodeSizeMultiplier
                                val dx = node.x - worldX
                                val dy = node.y - worldY
                                sqrt(dx * dx + dy * dy) <= r * 1.6f
                            }
                            if (hit != null) {
                                draggedNodeId = hit.id
                                hit.isPinned = true
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedNodeId != null) {
                                val n = localNodes.firstOrNull { it.id == draggedNodeId }
                                if (n != null) {
                                    n.x += dragAmount.x / scale
                                    n.y += dragAmount.y / scale
                                }
                            } else {
                                panOffset += dragAmount
                            }
                        },
                        onDragEnd = {
                            if (draggedNodeId != null) {
                                localNodes.firstOrNull { it.id == draggedNodeId }?.isPinned = false
                                draggedNodeId = null
                            }
                        },
                        onDragCancel = {
                            if (draggedNodeId != null) {
                                localNodes.firstOrNull { it.id == draggedNodeId }?.isPinned = false
                                draggedNodeId = null
                            }
                        }
                    )
                }
                .pointerInput(visibleNodes, scale, panOffset) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val worldX = (tapOffset.x - centerX - panOffset.x) / scale
                        val worldY = (tapOffset.y - centerY - panOffset.y) / scale

                        val tapped = visibleNodes.firstOrNull { node ->
                            val r = (14f + node.degree * 2.5f) * display.nodeSizeMultiplier
                            val dx = node.x - worldX
                            val dy = node.y - worldY
                            sqrt(dx * dx + dy * dy) <= r * 1.8f
                        }

                        selectedNodeId = if (selectedNodeId == tapped?.id) null else tapped?.id
                    }
                }
        ) {
            // Read simTick to ensure canvas updates during physics relaxation
            @Suppress("UNUSED_VARIABLE")
            val tick = simTick

            val centerX = size.width / 2f
            val centerY = size.height / 2f

            // Viewport world bounds for spatial culling
            val worldMinX = (-centerX - panOffset.x) / scale - 80f
            val worldMaxX = (size.width - centerX - panOffset.x) / scale + 80f
            val worldMinY = (-centerY - panOffset.y) / scale - 80f
            val worldMaxY = (size.height - centerY - panOffset.y) / scale + 80f

            val nodeMap = visibleNodes.associateBy { it.id }

            // 1. Draw Subtle Grid Pattern
            val dotSpacing = 42f * scale
            val startX = (panOffset.x % dotSpacing)
            val startY = (panOffset.y % dotSpacing)
            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(
                        color = Color(0x15FFFFFF),
                        radius = 1.0f,
                        center = Offset(gx, gy)
                    )
                    gy += dotSpacing
                }
                gx += dotSpacing
            }

            // 2. Draw Edges
            for (edge in visibleEdges) {
                val s = nodeMap[edge.sourceId]
                val t = nodeMap[edge.targetId]
                if (s != null && t != null) {
                    // Spatial check: skip edge if both endpoints are completely outside viewport
                    val sIn = s.x in worldMinX..worldMaxX && s.y in worldMinY..worldMaxY
                    val tIn = t.x in worldMinX..worldMaxX && t.y in worldMinY..worldMaxY
                    if (!sIn && !tIn) continue

                    val sx = centerX + panOffset.x + s.x * scale
                    val sy = centerY + panOffset.y + s.y * scale
                    val tx = centerX + panOffset.x + t.x * scale
                    val ty = centerY + panOffset.y + t.y * scale

                    val isConnectedToSelected = selectedNodeId != null &&
                            (selectedNodeId == s.id || selectedNodeId == t.id)
                    val isDimmed = selectedNodeId != null && !isConnectedToSelected

                    val edgeColor = when {
                        !edge.isResolved -> if (isDimmed) Color(0x20F59E0B) else Color(0x90F59E0B)
                        isConnectedToSelected -> ObsidianPurpleLight
                        isDimmed -> Color(0x18FFFFFF)
                        else -> ObsidianBorderLight
                    }

                    val strokeW = when {
                        isConnectedToSelected -> 2.4f * scale * display.lineThickness
                        isDimmed -> 0.7f * scale * display.lineThickness
                        else -> 1.1f * scale * display.lineThickness
                    }

                    drawLine(
                        color = edgeColor,
                        start = Offset(sx, sy),
                        end = Offset(tx, ty),
                        strokeWidth = strokeW
                    )

                    // Draw Directional Arrow
                    if (display.showArrows && !isDimmed) {
                        val dx = tx - sx
                        val dy = ty - sy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > 30f) {
                            val targetRadius = (10f + t.degree * 2.2f) * scale * display.nodeSizeMultiplier
                            val arrowDist = (dist - targetRadius - 4f).coerceAtLeast(10f)
                            val arrowX = sx + (dx / dist) * arrowDist
                            val arrowY = sy + (dy / dist) * arrowDist

                            val angle = atan2(dy.toDouble(), dx.toDouble()).toFloat()
                            val arrowSize = 6f * scale * display.lineThickness

                            val p1 = Offset(
                                (arrowX - arrowSize * cos(angle - 0.45)).toFloat(),
                                (arrowY - arrowSize * sin(angle - 0.45)).toFloat()
                            )
                            val p2 = Offset(
                                (arrowX - arrowSize * cos(angle + 0.45)).toFloat(),
                                (arrowY - arrowSize * sin(angle + 0.45)).toFloat()
                            )

                            val arrowPath = Path().apply {
                                moveTo(arrowX, arrowY)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                close()
                            }
                            drawPath(arrowPath, color = edgeColor)
                        }
                    }
                }
            }

            // 3. Prepare Text Paint for Labels
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (11f * scale).coerceIn(9f, 22f)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                alpha = 230
            }

            // 4. Draw Nodes
            for (node in visibleNodes) {
                // Spatial culling: skip nodes outside viewport
                if (node.x !in worldMinX..worldMaxX || node.y !in worldMinY..worldMaxY) {
                    continue
                }

                val nx = centerX + panOffset.x + node.x * scale
                val ny = centerY + panOffset.y + node.y * scale
                val baseRadius = (10f + (node.degree * 2.2f)) * scale * display.nodeSizeMultiplier

                val isSelected = selectedNodeId == node.id
                val isConnectedNeighbor = selectedConnectedIds.contains(node.id)
                val isDimmed = selectedNodeId != null && !isConnectedNeighbor

                val nodeBaseColor = resolveNodeColor(node, colorGroups, noteContentMap[node.path])
                val drawColor = if (isDimmed) nodeBaseColor.copy(alpha = 0.22f) else nodeBaseColor

                // Outer Halo if selected
                if (isSelected) {
                    drawCircle(
                        color = ObsidianPurpleGlow,
                        radius = baseRadius + 14f * scale,
                        center = Offset(nx, ny)
                    )
                    drawCircle(
                        color = ObsidianPurpleLight,
                        radius = baseRadius + 4f * scale,
                        center = Offset(nx, ny)
                    )
                } else if (isConnectedNeighbor && selectedNodeId != null) {
                    drawCircle(
                        color = drawColor.copy(alpha = 0.35f),
                        radius = baseRadius + 4f * scale,
                        center = Offset(nx, ny)
                    )
                }

                // Node Body
                when (node.nodeType) {
                    GraphNodeType.UNRESOLVED -> {
                        // Unresolved notes drawn with distinct ring
                        drawCircle(
                            color = ObsidianSurface,
                            radius = baseRadius,
                            center = Offset(nx, ny)
                        )
                        drawCircle(
                            color = drawColor,
                            radius = baseRadius,
                            center = Offset(nx, ny),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * scale)
                        )
                    }
                    GraphNodeType.TAG -> {
                        // Tags drawn as pill/circle
                        drawCircle(
                            color = drawColor,
                            radius = baseRadius * 0.85f,
                            center = Offset(nx, ny)
                        )
                    }
                    GraphNodeType.ATTACHMENT -> {
                        // Attachments drawn as diamond with contrasting border
                        val s = baseRadius * 1.15f
                        val diamondPath = Path().apply {
                            moveTo(nx, ny - s)
                            lineTo(nx + s, ny)
                            lineTo(nx, ny + s)
                            lineTo(nx - s, ny)
                            close()
                        }
                        drawPath(diamondPath, color = drawColor)
                        drawPath(
                            diamondPath,
                            color = ObsidianTextPrimary.copy(alpha = 0.7f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f * scale)
                        )
                    }
                    else -> {
                        // Standard Note
                        drawCircle(
                            color = drawColor,
                            radius = baseRadius,
                            center = Offset(nx, ny)
                        )
                    }
                }

                // Node Labels: drawn if zoomed in enough or if node is selected/focused
                val showLabel = (scale >= display.textFadeThreshold) || isSelected || isConnectedNeighbor
                if (showLabel && !isDimmed) {
                    drawIntoCanvas { canvas ->
                        textPaint.alpha = if (isSelected) 255 else if (isConnectedNeighbor) 220 else 180
                        canvas.nativeCanvas.drawText(
                            node.title,
                            nx,
                            ny + baseRadius + (12f * scale),
                            textPaint
                        )
                    }
                }
            }
        }

        // Top Toolbar Overlay: Graph Mode, Stats & Quick Actions
        Surface(
            color = ObsidianSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // Mode Toggle Pill
                FilterChip(
                    selected = display.isLocalGraph,
                    onClick = {
                        display = display.copy(isLocalGraph = !display.isLocalGraph)
                    },
                    label = {
                        Text(
                            if (display.isLocalGraph) "🎯 Local Graph (${display.localGraphDepth}x)" else "🌐 Global Vault",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (display.isLocalGraph) Icons.Default.CenterFocusStrong else Icons.Default.Hub,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${visibleNodes.size} Nodes · ${visibleEdges.size} Links",
                    fontSize = 12.sp,
                    color = ObsidianTextPrimary
                )

                if (display.isEcoMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(ObsidianYellow.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("🍃 Eco Physics", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ObsidianYellow)
                    }
                }
            }
        }

        // Floating Action Buttons (Top-End)
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Open Settings Dialog
            FloatingActionButton(
                onClick = { showSettingsDialog = true },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianPurpleLight,
                modifier = Modifier.size(40.dp).testTag("graph_open_settings_fab")
            ) {
                Icon(Icons.Default.Tune, contentDescription = "Settings", modifier = Modifier.size(20.dp))
            }

            // Pause/Play Simulation
            FloatingActionButton(
                onClick = { forces = forces.copy(isSimulating = !forces.isSimulating) },
                containerColor = ObsidianSurfaceElevated,
                contentColor = if (forces.isSimulating) ObsidianGreen else ObsidianTextMuted,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    if (forces.isSimulating) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (forces.isSimulating) "Pause Simulation" else "Resume Simulation",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom In
            FloatingActionButton(
                onClick = { scale = (scale * 1.25f).coerceAtMost(4.0f) },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { scale = (scale / 1.25f).coerceAtLeast(0.25f) },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }

            // Reset View
            FloatingActionButton(
                onClick = {
                    scale = 1.0f
                    panOffset = Offset.Zero
                    selectedNodeId = null
                },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset View", modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Selected Node Preview Card
        val selectedNode = remember(selectedNodeId, localNodes) {
            localNodes.find { it.id == selectedNodeId }
        }

        AnimatedVisibility(
            visible = selectedNode != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedNode?.let { node ->
                val matchingNote = allNotes.find {
                    it.path.equals(node.path, ignoreCase = true) || it.title.equals(node.title, ignoreCase = true)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ObsidianPurple)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("graph_selected_node_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(resolveNodeColor(node, colorGroups, noteContentMap[node.path]))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = node.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedNodeId = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (node.nodeType) {
                                    GraphNodeType.UNRESOLVED -> "⚠️ Unresolved Link (Not created)"
                                    GraphNodeType.TAG -> "🏷️ Tag Node"
                                    GraphNodeType.ATTACHMENT -> "📎 Attachment File"
                                    GraphNodeType.NOTE -> "📁 ${node.folder}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when (node.nodeType) {
                                    GraphNodeType.UNRESOLVED -> Color(0xFFF59E0B)
                                    GraphNodeType.ATTACHMENT -> Color(0xFFE5A93C)
                                    else -> ObsidianTextSecondary
                                }
                            )
                            Text("·", fontSize = 12.sp, color = ObsidianTextMuted)
                            Text("${node.degree} links", fontSize = 12.sp, color = ObsidianPurpleLight)
                        }

                        if (matchingNote != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = matchingNote.content.take(120).replace("\n", " "),
                                fontSize = 12.sp,
                                color = ObsidianTextMuted,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onOpenNote(matchingNote) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("graph_open_note_button")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Note in Editor", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        display = display.copy(isLocalGraph = true)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextPrimary)
                                ) {
                                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Focus Local", fontSize = 12.sp)
                                }
                            }
                        } else if (node.nodeType == GraphNodeType.UNRESOLVED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This note was referenced in a [[${node.title}]] wikilink, but does not exist in the vault yet. Click below to create it.",
                                fontSize = 12.sp,
                                color = ObsidianTextMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    onOpenNoteByTitle(node.title)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("graph_create_unresolved_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Note in Vault", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        } else if (node.nodeType == GraphNodeType.ATTACHMENT) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Media / Document attachment in vault (${node.path}). Linked in ${node.degree} notes.",
                                fontSize = 12.sp,
                                color = ObsidianTextMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    display = display.copy(isLocalGraph = true)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ObsidianTextPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("graph_focus_attachment_button")
                            ) {
                                Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Focus Local Graph on Attachment", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Graph Settings Dialog
    if (showSettingsDialog) {
        GraphSettingsDialog(
            filters = filters,
            forces = forces,
            display = display,
            colorGroups = colorGroups,
            onUpdateFilters = { filters = it },
            onUpdateForces = { forces = it },
            onUpdateDisplay = { display = it },
            onAddColorGroup = { colorGroups = colorGroups + it },
            onRemoveColorGroup = { id -> colorGroups = colorGroups.filter { it.id != id } },
            onResetForces = { forces = GraphForcesConfig() },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// Helper to resolve node colors from custom Color Groups or default Obsidian palette
private fun resolveNodeColor(node: GraphNode, colorGroups: List<GraphColorGroup>, noteContent: String?): Color {
    // 1. Check custom color group rules using Obsidian graph query engine
    for (group in colorGroups) {
        if (ObsidianGraphQueryParser.matches(node, noteContent, group.query)) {
            return group.color
        }
    }

    // 2. Default Obsidian color palette fallback
    return when (node.nodeType) {
        GraphNodeType.UNRESOLVED -> Color(0xFFF59E0B) // Amber for ghost links
        GraphNodeType.TAG -> ObsidianCyan
        GraphNodeType.ATTACHMENT -> Color(0xFFE5A93C) // Warm amber for attachments
        GraphNodeType.NOTE -> when (node.folder) {
            "Concepts" -> ObsidianPurple
            "Systems" -> ObsidianTeal
            "Wiki" -> ObsidianYellow
            "Daily Notes" -> ObsidianGreen
            else -> ObsidianPurpleLight
        }
    }
}
