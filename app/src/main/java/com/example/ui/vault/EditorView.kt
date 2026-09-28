package com.example.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BacklinkItem
import com.example.data.model.VaultNote
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorView(
    note: VaultNote?,
    isLivePreview: Boolean,
    showBacklinks: Boolean,
    linkedMentions: List<BacklinkItem>,
    unlinkedMentions: List<BacklinkItem>,
    onContentChanged: (String) -> Unit,
    onTogglePreview: () -> Unit,
    onToggleBacklinks: () -> Unit,
    onWikilinkClicked: (String) -> Unit,
    onSuggestLinks: () -> Unit,
    onToggleBookmark: (VaultNote) -> Unit,
    onDeleteNote: () -> Unit,
    onOpenMentionNote: (VaultNote) -> Unit,
    modifier: Modifier = Modifier
) {
    if (note == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.EditNote,
                    contentDescription = "No note selected",
                    tint = ObsidianTextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No note selected",
                    style = MaterialTheme.typography.titleMedium.copy(color = ObsidianTextSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Select a note from the Explorer or create a new one",
                    style = MaterialTheme.typography.bodySmall.copy(color = ObsidianTextMuted)
                )
            }
        }
        return
    }

    var textFieldValue by remember(note.id) {
        mutableStateOf(TextFieldValue(note.content))
    }

    // Keep text field in sync if note content was updated externally
    LaunchedEffect(note.content) {
        if (textFieldValue.text != note.content) {
            textFieldValue = textFieldValue.copy(text = note.content)
        }
    }

    // Compute stats
    val wordCount = remember(note.content) {
        note.content.split("\\s+".toRegex()).count { it.isNotBlank() }
    }
    val charCount = note.content.length
    val readingTimeMin = (wordCount / 200).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Top Document Bar
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = ObsidianSurfaceElevated,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = note.folder,
                                    fontSize = 11.sp,
                                    color = ObsidianPurpleLight,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$wordCount words · $charCount chars · ~${readingTimeMin}m read",
                                fontSize = 11.sp,
                                color = ObsidianTextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )
                        )
                    }

                    // Toolbar Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AI Suggest Links
                        IconButton(
                            onClick = onSuggestLinks,
                            modifier = Modifier.testTag("ai_suggest_links_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Suggest Links with AI",
                                tint = ObsidianYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Toggle Backlinks Panel
                        IconButton(
                            onClick = onToggleBacklinks,
                            modifier = Modifier.testTag("toggle_backlinks_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    val count = linkedMentions.size + unlinkedMentions.size
                                    if (count > 0) {
                                        Badge(
                                            containerColor = ObsidianPurple,
                                            contentColor = Color.White
                                        ) {
                                            Text("$count", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (showBacklinks) Icons.Default.Link else Icons.Outlined.Link,
                                    contentDescription = "Backlinks",
                                    tint = if (showBacklinks) ObsidianPurpleLight else ObsidianTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Toggle Live Preview / Edit
                        IconButton(
                            onClick = onTogglePreview,
                            modifier = Modifier.testTag("toggle_preview_button")
                        ) {
                            Icon(
                                imageVector = if (isLivePreview) Icons.Default.Edit else Icons.Default.Visibility,
                                contentDescription = if (isLivePreview) "Switch to Edit" else "Switch to Preview",
                                tint = ObsidianPurpleLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Star
                        IconButton(
                            onClick = { onToggleBookmark(note) },
                            modifier = Modifier.testTag("bookmark_note_button")
                        ) {
                            Icon(
                                imageVector = if (note.isBookmarked) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Bookmark",
                                tint = if (note.isBookmarked) ObsidianYellow else ObsidianTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Quick Format Toolbar (Visible in edit mode)
                AnimatedVisibility(visible = !isLivePreview) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianSurfaceElevated)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FormatToolButton("H1", "# ") { insertPrefix("# ", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("H2", "## ") { insertPrefix("## ", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("Bold", "**") { wrapSelection("**", "**", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("Italic", "*") { wrapSelection("*", "*", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("[[Wikilink]]", "[[]]") { wrapSelection("[[", "]]", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("- [ ] Task", "- [ ] ") { insertPrefix("- [ ] ", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("• List", "- ") { insertPrefix("- ", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("`Code`", "`") { wrapSelection("`", "`", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                        FormatToolButton("> Quote", "> ") { insertPrefix("> ", textFieldValue) { textFieldValue = it; onContentChanged(it.text) } }
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        // Main Editor / Preview Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (isLivePreview) {
                // Live Rendered Markdown Mode
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    MarkdownRenderer(
                        content = note.content,
                        onWikilinkClicked = onWikilinkClicked,
                        onCheckboxToggled = { lineIndex, isChecked ->
                            // Toggle checkbox line
                            val lines = note.content.lines().toMutableList()
                            if (lineIndex < lines.size) {
                                val currentLine = lines[lineIndex]
                                lines[lineIndex] = if (isChecked) {
                                    currentLine.replaceFirst("- [ ] ", "- [x] ")
                                } else {
                                    currentLine.replaceFirst("- [x] ", "- [ ] ").replaceFirst("- [X] ", "- [ ] ")
                                }
                                val newContent = lines.joinToString("\n")
                                onContentChanged(newContent)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                }
            } else {
                // Raw Markdown Edit Mode
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onContentChanged(newValue.text)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("note_content_editor"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = ObsidianBackground,
                        unfocusedContainerColor = ObsidianBackground,
                        focusedTextColor = ObsidianTextPrimary,
                        unfocusedTextColor = ObsidianTextPrimary
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = ObsidianTextPrimary
                    )
                )
            }
        }

        // Backlinks Inspector Drawer / Panel
        AnimatedVisibility(visible = showBacklinks) {
            Surface(
                color = ObsidianSurfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .border(width = 1.dp, color = ObsidianBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Backlinks for \"${note.title}\"",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianPurpleLight
                            )
                        )
                        IconButton(onClick = onToggleBacklinks, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = ObsidianTextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Linked Mentions
                    Text(
                        text = "LINKED MENTIONS (${linkedMentions.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (linkedMentions.isEmpty()) {
                        Text("No other notes link to this note via [[]].", fontSize = 11.sp, color = ObsidianTextMuted)
                    } else {
                        linkedMentions.forEach { mention ->
                            Card(
                                onClick = { onOpenMentionNote(mention.note) },
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = mention.note.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = ObsidianPurpleLight,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = mention.previewSnippet,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ObsidianTextSecondary,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Unlinked Mentions
                    Text(
                        text = "UNLINKED MENTIONS (${unlinkedMentions.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (unlinkedMentions.isEmpty()) {
                        Text("No unlinked textual mentions found.", fontSize = 11.sp, color = ObsidianTextMuted)
                    } else {
                        unlinkedMentions.forEach { mention ->
                            Card(
                                onClick = { onOpenMentionNote(mention.note) },
                                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = mention.note.title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = ObsidianTextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = mention.previewSnippet,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ObsidianTextMuted,
                                            fontSize = 11.sp
                                        ),
                                        maxLines = 2
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

@Composable
fun FormatToolButton(label: String, tooltip: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = ObsidianSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.height(28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = ObsidianTextPrimary
            )
        }
    }
}

// Helpers for editor formatting insertions
fun wrapSelection(prefix: String, suffix: String, value: TextFieldValue, onUpdate: (TextFieldValue) -> Unit) {
    val text = value.text
    val selection = value.selection
    val newText = if (selection.collapsed) {
        text.substring(0, selection.start) + prefix + suffix + text.substring(selection.end)
    } else {
        text.substring(0, selection.start) + prefix + text.substring(selection.start, selection.end) + suffix + text.substring(selection.end)
    }
    val newCursor = if (selection.collapsed) selection.start + prefix.length else selection.end + prefix.length + suffix.length
    onUpdate(TextFieldValue(newText, TextRange(newCursor)))
}

fun insertPrefix(prefix: String, value: TextFieldValue, onUpdate: (TextFieldValue) -> Unit) {
    val text = value.text
    val selection = value.selection
    val lineStart = text.lastIndexOf('\n', selection.start - 1) + 1
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    onUpdate(TextFieldValue(newText, TextRange(selection.start + prefix.length)))
}
