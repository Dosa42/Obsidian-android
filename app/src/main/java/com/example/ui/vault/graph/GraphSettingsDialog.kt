package com.example.ui.vault.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphSettingsDialog(
    filters: GraphFilterConfig,
    forces: GraphForcesConfig,
    display: GraphDisplayConfig,
    colorGroups: List<GraphColorGroup>,
    onUpdateFilters: (GraphFilterConfig) -> Unit,
    onUpdateForces: (GraphForcesConfig) -> Unit,
    onUpdateDisplay: (GraphDisplayConfig) -> Unit,
    onAddColorGroup: (GraphColorGroup) -> Unit,
    onRemoveColorGroup: (String) -> Unit,
    onResetForces: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Filters, 1 = Forces, 2 = Display, 3 = Color Groups, 4 = Local Graph

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Graph Settings",
                            tint = ObsidianPurpleLight,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Graph View Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_graph_settings_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary)
                    }
                }

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = ObsidianPurpleLight,
                    edgePadding = 8.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Filters", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Forces", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Display", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Groups", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("Local Graph", fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Filters Tab
                            Text(
                                text = "Search & Filter Vault Elements",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )

                            OutlinedTextField(
                                value = filters.searchQuery,
                                onValueChange = { onUpdateFilters(filters.copy(searchQuery = it)) },
                                label = { Text("Search file or tag name") },
                                placeholder = { Text("e.g. Concept or #project") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ObsidianTextSecondary) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("graph_search_filter_input")
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterToggleRow(
                                        title = "Existing Notes",
                                        subtitle = "Show notes that exist in your Obsidian vault",
                                        checked = filters.showExistingNotes,
                                        onCheckedChange = { onUpdateFilters(filters.copy(showExistingNotes = it)) }
                                    )
                                    HorizontalDivider(color = ObsidianBorder)
                                    FilterToggleRow(
                                        title = "Unresolved Links",
                                        subtitle = "Show links to notes that do not exist yet (ghost nodes)",
                                        checked = filters.showUnresolvedNotes,
                                        onCheckedChange = { onUpdateFilters(filters.copy(showUnresolvedNotes = it)) }
                                    )
                                    HorizontalDivider(color = ObsidianBorder)
                                    FilterToggleRow(
                                        title = "Orphans",
                                        subtitle = "Show notes with zero connections",
                                        checked = filters.showOrphans,
                                        onCheckedChange = { onUpdateFilters(filters.copy(showOrphans = it)) }
                                    )
                                    HorizontalDivider(color = ObsidianBorder)
                                    FilterToggleRow(
                                        title = "Tags",
                                        subtitle = "Display #tag nodes connected to tagged notes",
                                        checked = filters.showTags,
                                        onCheckedChange = { onUpdateFilters(filters.copy(showTags = it)) }
                                    )
                                    HorizontalDivider(color = ObsidianBorder)
                                    FilterToggleRow(
                                        title = "Attachments",
                                        subtitle = "Display images, audio, video and document nodes linked in vault",
                                        checked = filters.showAttachments,
                                        onCheckedChange = { onUpdateFilters(filters.copy(showAttachments = it)) }
                                    )
                                }
                            }
                        }

                        1 -> {
                            // Forces Tab
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Simulation Forces (Physics Engine)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ObsidianTextPrimary
                                )
                                TextButton(onClick = onResetForces) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset Defaults", fontSize = 12.sp)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    ForceSliderRow(
                                        title = "Center Force",
                                        value = forces.centerForce,
                                        valueRange = 0.001f..0.030f,
                                        valueLabel = String.format("%.3f", forces.centerForce),
                                        onValueChange = { onUpdateForces(forces.copy(centerForce = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Repel Force (Node spacing)",
                                        value = forces.repelForce,
                                        valueRange = 600f..5500f,
                                        valueLabel = forces.repelForce.toInt().toString(),
                                        onValueChange = { onUpdateForces(forces.copy(repelForce = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Link Force (Spring tension)",
                                        value = forces.linkForce,
                                        valueRange = 0.005f..0.12f,
                                        valueLabel = String.format("%.3f", forces.linkForce),
                                        onValueChange = { onUpdateForces(forces.copy(linkForce = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Link Distance",
                                        value = forces.linkDistance,
                                        valueRange = 40f..260f,
                                        valueLabel = "${forces.linkDistance.toInt()} px",
                                        onValueChange = { onUpdateForces(forces.copy(linkDistance = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Friction / Damping",
                                        value = forces.damping,
                                        valueRange = 0.70f..0.96f,
                                        valueLabel = String.format("%.2f", forces.damping),
                                        onValueChange = { onUpdateForces(forces.copy(damping = it)) }
                                    )

                                    HorizontalDivider(color = ObsidianBorder)

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Live Simulation", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary)
                                            Text("Calculates force relaxation smoothly in real time", fontSize = 11.sp, color = ObsidianTextSecondary)
                                        }
                                        Switch(
                                            checked = forces.isSimulating,
                                            onCheckedChange = { onUpdateForces(forces.copy(isSimulating = it)) }
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Display Tab
                            Text(
                                text = "Visual Display & Rendering",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    FilterToggleRow(
                                        title = "Directional Arrows",
                                        subtitle = "Show arrows indicating wikilink direction",
                                        checked = display.showArrows,
                                        onCheckedChange = { onUpdateDisplay(display.copy(showArrows = it)) }
                                    )
                                    HorizontalDivider(color = ObsidianBorder)
                                    ForceSliderRow(
                                        title = "Node Size Multiplier",
                                        value = display.nodeSizeMultiplier,
                                        valueRange = 0.6f..2.2f,
                                        valueLabel = String.format("%.1fx", display.nodeSizeMultiplier),
                                        onValueChange = { onUpdateDisplay(display.copy(nodeSizeMultiplier = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Line Thickness",
                                        value = display.lineThickness,
                                        valueRange = 0.5f..2.5f,
                                        valueLabel = String.format("%.1fx", display.lineThickness),
                                        onValueChange = { onUpdateDisplay(display.copy(lineThickness = it)) }
                                    )
                                    ForceSliderRow(
                                        title = "Text Fade Threshold (Zoom level)",
                                        value = display.textFadeThreshold,
                                        valueRange = 0.3f..1.2f,
                                        valueLabel = String.format("%.2f", display.textFadeThreshold),
                                        onValueChange = { onUpdateDisplay(display.copy(textFadeThreshold = it)) }
                                    )
                                }
                            }
                        }

                        3 -> {
                            // Groups Tab
                            Text(
                                text = "Color Groups (Custom Query Rules)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )
                            Text(
                                text = "Assign custom colors to notes matching folder, tag, or title queries.",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )

                            var newRuleLabel by remember { mutableStateOf("") }
                            var newRuleQuery by remember { mutableStateOf("") }
                            var selectedColor by remember { mutableStateOf(ObsidianPurple) }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    colorGroups.forEach { group ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(group.color)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(group.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary)
                                                    Text(group.query, fontSize = 10.sp, color = ObsidianTextSecondary)
                                                }
                                            }
                                            IconButton(
                                                onClick = { onRemoveColorGroup(group.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        HorizontalDivider(color = ObsidianBorder)
                                    }

                                    // Add new group rule
                                    Text("Add Color Rule:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ObsidianTextSecondary)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = newRuleLabel,
                                            onValueChange = { newRuleLabel = it },
                                            placeholder = { Text("Group Name", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = newRuleQuery,
                                            onValueChange = { newRuleQuery = it },
                                            placeholder = { Text("Query: folder:Work or #tag", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val palette = listOf(ObsidianPurple, ObsidianTeal, ObsidianGreen, ObsidianYellow, ObsidianCyan, ObsidianRed, Color(0xFFF97316), Color(0xFFEC4899))
                                        palette.forEach { c ->
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(c)
                                                    .clickable { selectedColor = c }
                                                    .border(
                                                        width = if (selectedColor == c) 2.dp else 0.dp,
                                                        color = if (selectedColor == c) Color.White else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                if (newRuleLabel.isNotBlank() && newRuleQuery.isNotBlank()) {
                                                    onAddColorGroup(
                                                        GraphColorGroup(
                                                            id = System.currentTimeMillis().toString(),
                                                            label = newRuleLabel.trim(),
                                                            query = newRuleQuery.trim(),
                                                            color = selectedColor
                                                        )
                                                    )
                                                    newRuleLabel = ""
                                                    newRuleQuery = ""
                                                }
                                            },
                                            enabled = newRuleLabel.isNotBlank() && newRuleQuery.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianPurpleLight)
                                        ) {
                                            Text("Add", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // Local Graph Tab
                            Text(
                                text = "Local Graph Mode (Active Note Neighborhood)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )
                            Text(
                                text = "Filters graph to show only the active note and its direct incoming/outgoing connections up to N steps away.",
                                fontSize = 11.sp,
                                color = ObsidianTextSecondary
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    FilterToggleRow(
                                        title = "Enable Local Graph",
                                        subtitle = "Show connections around focused note instead of full vault",
                                        checked = display.isLocalGraph,
                                        onCheckedChange = { onUpdateDisplay(display.copy(isLocalGraph = it)) }
                                    )

                                    if (display.isLocalGraph) {
                                        HorizontalDivider(color = ObsidianBorder)
                                        ForceSliderRow(
                                            title = "Connection Depth (Hops)",
                                            value = display.localGraphDepth.toFloat(),
                                            valueRange = 1f..5f,
                                            valueLabel = "${display.localGraphDepth} step${if (display.localGraphDepth > 1) "s" else ""}",
                                            onValueChange = { onUpdateDisplay(display.copy(localGraphDepth = it.toInt())) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ObsidianTextPrimary)
            Text(subtitle, fontSize = 11.sp, color = ObsidianTextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ForceSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ObsidianTextPrimary)
            Text(valueLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ObsidianPurpleLight)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = ObsidianPurpleLight,
                activeTrackColor = ObsidianPurple
            )
        )
    }
}
