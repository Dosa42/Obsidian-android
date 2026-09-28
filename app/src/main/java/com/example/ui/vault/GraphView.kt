package com.example.ui.vault

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GraphNode
import com.example.data.model.VaultNote
import com.example.data.repository.VaultGraphData
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GraphView(
    graphData: VaultGraphData,
    allNotes: List<VaultNote>,
    onOpenNote: (VaultNote) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var draggedNode by remember { mutableStateOf<GraphNode?>(null) }

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
                vy = 0f
            )
        }.toMutableStateList()
    }

    // Force-directed layout physics simulation loop
    LaunchedEffect(graphData) {
        var iterations = 0
        while (iterations < 160) {
            val nodeMap = localNodes.associateBy { it.id }
            val kRepel = 2400f
            val kSpring = 0.04f
            val kCenter = 0.008f
            val damping = 0.82f

            // Repulsion between all node pairs
            for (i in 0 until localNodes.size) {
                val n1 = localNodes[i]
                for (j in i + 1 until localNodes.size) {
                    val n2 = localNodes[j]
                    val dx = n2.x - n1.x
                    val dy = n2.y - n1.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(15f)
                    val force = kRepel / (dist * dist)
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    n1.vx -= fx
                    n1.vy -= fy
                    n2.vx += fx
                    n2.vy += fy
                }
            }

            // Spring attraction along edges
            for (edge in graphData.edges) {
                val s = nodeMap[edge.sourceId]
                val t = nodeMap[edge.targetId]
                if (s != null && t != null) {
                    val dx = t.x - s.x
                    val dy = t.y - s.y
                    val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val targetDist = 140f
                    val displacement = dist - targetDist
                    val force = displacement * kSpring
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force
                    s.vx += fx
                    s.vy += fy
                    t.vx -= fx
                    t.vy -= fy
                }
            }

            // Center gravity & apply velocities
            for (node in localNodes) {
                if (node != draggedNode) {
                    node.vx -= node.x * kCenter
                    node.vy -= node.y * kCenter
                    node.vx *= damping
                    node.vy *= damping
                    node.x += node.vx
                    node.y += node.vy
                }
            }

            iterations++
            delay(16)
        }
    }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.3f, 3.5f)
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
                .pointerInput(localNodes) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            val worldX = (offset.x - centerX - panOffset.x) / scale
                            val worldY = (offset.y - centerY - panOffset.y) / scale

                            draggedNode = localNodes.firstOrNull { node ->
                                val r = 18f + (node.degree * 4f)
                                val dx = node.x - worldX
                                val dy = node.y - worldY
                                sqrt(dx * dx + dy * dy) <= r * 1.5f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (draggedNode != null) {
                                draggedNode?.let { n ->
                                    n.x += dragAmount.x / scale
                                    n.y += dragAmount.y / scale
                                }
                            } else {
                                panOffset += dragAmount
                            }
                        },
                        onDragEnd = {
                            draggedNode = null
                        },
                        onDragCancel = {
                            draggedNode = null
                        }
                    )
                }
                .pointerInput(localNodes) {
                    detectTapGestures { tapOffset ->
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val worldX = (tapOffset.x - centerX - panOffset.x) / scale
                        val worldY = (tapOffset.y - centerY - panOffset.y) / scale

                        val tapped = localNodes.firstOrNull { node ->
                            val r = 18f + (node.degree * 4f)
                            val dx = node.x - worldX
                            val dy = node.y - worldY
                            sqrt(dx * dx + dy * dy) <= r * 1.8f
                        }
                        selectedNode = tapped
                    }
                }
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            val nodeMap = localNodes.associateBy { it.id }

            // Draw Background Subtle Grid Dots
            val dotSpacing = 40f * scale
            val startX = (panOffset.x % dotSpacing)
            val startY = (panOffset.y % dotSpacing)
            var gx = startX
            while (gx < size.width) {
                var gy = startY
                while (gy < size.height) {
                    drawCircle(
                        color = Color(0x18FFFFFF),
                        radius = 1.2f,
                        center = Offset(gx, gy)
                    )
                    gy += dotSpacing
                }
                gx += dotSpacing
            }

            // Draw Edges
            for (edge in graphData.edges) {
                val s = nodeMap[edge.sourceId]
                val t = nodeMap[edge.targetId]
                if (s != null && t != null) {
                    val sx = centerX + panOffset.x + s.x * scale
                    val sy = centerY + panOffset.y + s.y * scale
                    val tx = centerX + panOffset.x + t.x * scale
                    val ty = centerY + panOffset.y + t.y * scale

                    val isConnectedToSelected = selectedNode != null &&
                            (selectedNode?.id == s.id || selectedNode?.id == t.id)

                    val edgeColor = if (isConnectedToSelected) ObsidianPurpleLight else ObsidianBorderLight
                    val strokeW = if (isConnectedToSelected) 2.2f * scale else 1.0f * scale

                    drawLine(
                        color = edgeColor,
                        start = Offset(sx, sy),
                        end = Offset(tx, ty),
                        strokeWidth = strokeW
                    )
                }
            }

            // Text Paint for Labels
            val textPaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (11f * scale).coerceIn(9f, 22f)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                alpha = 220
            }

            // Draw Nodes
            for (node in localNodes) {
                val nx = centerX + panOffset.x + node.x * scale
                val ny = centerY + panOffset.y + node.y * scale
                val baseRadius = (10f + (node.degree * 2.8f)) * scale
                val isSelected = selectedNode?.id == node.id

                val nodeColor = when (node.folder) {
                    "Concepts" -> ObsidianPurple
                    "Systems" -> ObsidianTeal
                    "Wiki" -> ObsidianYellow
                    "Daily Notes" -> ObsidianGreen
                    else -> ObsidianPurpleLight
                }

                // Outer Halo if selected
                if (isSelected) {
                    drawCircle(
                        color = ObsidianPurpleGlow,
                        radius = baseRadius + 12f * scale,
                        center = Offset(nx, ny)
                    )
                    drawCircle(
                        color = ObsidianPurpleLight,
                        radius = baseRadius + 3f * scale,
                        center = Offset(nx, ny)
                    )
                }

                // Node Body
                drawCircle(
                    color = nodeColor,
                    radius = baseRadius,
                    center = Offset(nx, ny)
                )

                // Label Text
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        node.title,
                        nx,
                        ny + baseRadius + (13f * scale),
                        textPaint
                    )
                }
            }
        }

        // Top Toolbar Overlay: Graph stats & Controls
        Surface(
            color = ObsidianSurface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Hub,
                    contentDescription = "Graph View",
                    tint = ObsidianPurpleLight,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${localNodes.size} Nodes · ${graphData.edges.size} Links",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ObsidianTextPrimary
                )
            }
        }

        // Floating Zoom Controls
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { scale = (scale * 1.25f).coerceAtMost(3.5f) },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }
            FloatingActionButton(
                onClick = { scale = (scale / 1.25f).coerceAtLeast(0.3f) },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
            FloatingActionButton(
                onClick = {
                    scale = 1.0f
                    panOffset = Offset.Zero
                },
                containerColor = ObsidianSurfaceElevated,
                contentColor = ObsidianTextPrimary,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset View", modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Selected Node Preview Card
        AnimatedVisibility(
            visible = selectedNode != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedNode?.let { node ->
                val matchingNote = allNotes.find { it.title.equals(node.title, ignoreCase = true) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
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
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianPurpleLight)
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
                                onClick = { selectedNode = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Folder: ${node.folder} · Degree: ${node.degree} links",
                            fontSize = 12.sp,
                            color = ObsidianTextSecondary
                        )

                        if (matchingNote != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = matchingNote.content.take(120).replace("\n", " "),
                                fontSize = 12.sp,
                                color = ObsidianTextMuted,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onOpenNote(matchingNote) },
                                colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("graph_open_note_button")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Note in Editor", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
