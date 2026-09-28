package com.example.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VaultNote
import com.example.data.repository.SearchResult
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchView(
    searchQuery: String,
    searchResults: List<SearchResult>,
    allTags: List<String>,
    selectedTagFilter: String?,
    onQueryChanged: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onOpenNote: (VaultNote) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBackground)
    ) {
        // Search Header
        Surface(
            color = ObsidianSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ObsidianPurpleLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INTELLIGENT SEMANTIC SEARCH",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = ObsidianTextSecondary
                        )
                    )
                }

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onQueryChanged,
                    placeholder = { Text("Search vault notes, tags, or concepts...", color = ObsidianTextMuted, fontSize = 14.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = ObsidianTextSecondary)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianSurfaceElevated,
                        unfocusedContainerColor = ObsidianSurfaceElevated,
                        focusedBorderColor = ObsidianPurple,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = ObsidianTextPrimary,
                        unfocusedTextColor = ObsidianTextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("semantic_search_input")
                )

                // Tag Filter Chips
                if (allTags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allTags) { tag ->
                            val isSelected = selectedTagFilter == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTagSelected(tag) },
                                label = { Text("#$tag", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ObsidianPurpleContainer,
                                    selectedLabelColor = ObsidianPurpleLight,
                                    containerColor = ObsidianSurfaceElevated,
                                    labelColor = ObsidianTextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isSelected) ObsidianPurple else ObsidianBorder,
                                    enabled = true,
                                    selected = isSelected
                                ),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianBorder, thickness = 1.dp)

        // Results
        if (searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.ManageSearch,
                        contentDescription = "Search",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Query your digital garden",
                        style = MaterialTheme.typography.titleMedium.copy(color = ObsidianTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try searching for:",
                        style = MaterialTheme.typography.bodySmall.copy(color = ObsidianTextMuted)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        SuggestionChip(
                            onClick = { onQueryChanged("Knowledge Graphs") },
                            label = { Text("Knowledge Graphs", fontSize = 12.sp, color = ObsidianPurpleLight) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = ObsidianSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        )
                        SuggestionChip(
                            onClick = { onQueryChanged("Venice AI") },
                            label = { Text("Venice AI", fontSize = 12.sp, color = ObsidianTeal) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = ObsidianSurfaceElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianBorder)
                        )
                    }
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = "No results",
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No matching notes found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ObsidianTextSecondary)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "${searchResults.size} results found",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ObsidianTextMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                items(searchResults) { result ->
                    SearchResultCard(
                        result = result,
                        onClick = { onOpenNote(result.note) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(ObsidianBorder)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_result_${result.note.title.replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = ObsidianTextPrimary
                    )
                )

                // Relevance Pill
                val relevancePct = (result.score * 4.5).coerceIn(40.0, 99.0).toInt()
                Surface(
                    color = ObsidianPurpleContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Score: $relevancePct%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ObsidianPurpleLight,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Folder: ${result.note.folder}",
                    fontSize = 11.sp,
                    color = ObsidianTextMuted
                )
                if (result.note.tags.isNotEmpty()) {
                    Text(
                        text = "·",
                        fontSize = 11.sp,
                        color = ObsidianTextMuted
                    )
                    Text(
                        text = result.note.tags.joinToString(" ") { "#$it" },
                        fontSize = 11.sp,
                        color = ObsidianYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Snippet with Highlighted terms
            val highlightedSnippet = buildAnnotatedString {
                val snipLower = result.snippet.lowercase()
                var lastIndex = 0
                val sortedTerms = result.matchedTerms.sortedBy { snipLower.indexOf(it) }

                for (term in sortedTerms) {
                    val idx = snipLower.indexOf(term, lastIndex)
                    if (idx != -1) {
                        append(result.snippet.substring(lastIndex, idx))
                        withStyle(
                            SpanStyle(
                                background = ObsidianPurpleContainer,
                                color = ObsidianPurpleLight,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(result.snippet.substring(idx, idx + term.length))
                        }
                        lastIndex = idx + term.length
                    }
                }
                if (lastIndex < result.snippet.length) {
                    append(result.snippet.substring(lastIndex))
                }
            }

            Text(
                text = highlightedSnippet,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ObsidianTextSecondary,
                    lineHeight = 18.sp
                )
            )
        }
    }
}
