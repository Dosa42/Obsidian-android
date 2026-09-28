package com.example.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
    val bookmarkedNotes by viewModel.bookmarkedNotes.collectAsStateWithLifecycle()
    val activeNote by viewModel.activeNote.collectAsStateWithLifecycle()
    val isLivePreview by viewModel.isLivePreview.collectAsStateWithLifecycle()
    val showBacklinks by viewModel.showBacklinks.collectAsStateWithLifecycle()
    val linkedMentions by viewModel.linkedMentions.collectAsStateWithLifecycle()
    val unlinkedMentions by viewModel.unlinkedMentions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedTagFilter by viewModel.selectedTagFilter.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val graphData by viewModel.graphData.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val chatModel by viewModel.chatModel.collectAsStateWithLifecycle()
    val deviceTelemetry by viewModel.deviceTelemetry.collectAsStateWithLifecycle()
    val storageAudit by viewModel.storageAudit.collectAsStateWithLifecycle()

    var showAndroidSkillsDialog by remember { mutableStateOf(false) }

    // Back handler: pop back to explorer if on another tab
    BackHandler(enabled = activeTab != VaultTab.EXPLORER) {
        viewModel.selectTab(VaultTab.EXPLORER)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Diamond,
                            contentDescription = "Obsidian Logo",
                            tint = ObsidianPurpleLight,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Obsidian Vault",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary
                            )
                        )
                        if (activeTab == VaultTab.EDITOR && activeNote != null) {
                            Text(
                                text = " · ${activeNote!!.title}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = ObsidianTextSecondary,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = ObsidianTextPrimary
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.selectTab(VaultTab.SEARCH) },
                        modifier = Modifier.testTag("top_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (activeTab == VaultTab.SEARCH) ObsidianPurpleLight else ObsidianTextSecondary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.selectTab(VaultTab.GRAPH) },
                        modifier = Modifier.testTag("top_graph_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = "Graph View",
                            tint = if (activeTab == VaultTab.GRAPH) ObsidianPurpleLight else ObsidianTextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.refreshDeviceTelemetry()
                            viewModel.loadStorageAudit()
                            showAndroidSkillsDialog = true
                        },
                        modifier = Modifier.testTag("top_android_skills_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = "Android Skills & Hooks",
                            tint = ObsidianGreen
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                contentColor = ObsidianTextPrimary,
                tonalElevation = 0.dp,
                modifier = Modifier.height(68.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == VaultTab.EXPLORER,
                    onClick = { viewModel.selectTab(VaultTab.EXPLORER) },
                    icon = {
                        Icon(
                            if (activeTab == VaultTab.EXPLORER) Icons.Default.Folder else Icons.Outlined.Folder,
                            contentDescription = "Explorer"
                        )
                    },
                    label = { Text("Explorer", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianPurpleLight,
                        selectedTextColor = ObsidianPurpleLight,
                        indicatorColor = ObsidianPurpleContainer,
                        unselectedIconColor = ObsidianTextSecondary,
                        unselectedTextColor = ObsidianTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_explorer")
                )

                NavigationBarItem(
                    selected = activeTab == VaultTab.EDITOR,
                    onClick = { viewModel.selectTab(VaultTab.EDITOR) },
                    icon = {
                        Icon(
                            if (activeTab == VaultTab.EDITOR) Icons.Default.EditNote else Icons.Outlined.EditNote,
                            contentDescription = "Editor"
                        )
                    },
                    label = { Text("Editor", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianPurpleLight,
                        selectedTextColor = ObsidianPurpleLight,
                        indicatorColor = ObsidianPurpleContainer,
                        unselectedIconColor = ObsidianTextSecondary,
                        unselectedTextColor = ObsidianTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_editor")
                )

                NavigationBarItem(
                    selected = activeTab == VaultTab.GRAPH,
                    onClick = { viewModel.selectTab(VaultTab.GRAPH) },
                    icon = {
                        Icon(
                            if (activeTab == VaultTab.GRAPH) Icons.Default.Hub else Icons.Outlined.Hub,
                            contentDescription = "Graph"
                        )
                    },
                    label = { Text("Graph", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianPurpleLight,
                        selectedTextColor = ObsidianPurpleLight,
                        indicatorColor = ObsidianPurpleContainer,
                        unselectedIconColor = ObsidianTextSecondary,
                        unselectedTextColor = ObsidianTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_graph")
                )

                NavigationBarItem(
                    selected = activeTab == VaultTab.SEARCH,
                    onClick = { viewModel.selectTab(VaultTab.SEARCH) },
                    icon = {
                        Icon(
                            if (activeTab == VaultTab.SEARCH) Icons.Default.Search else Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    },
                    label = { Text("Search", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianPurpleLight,
                        selectedTextColor = ObsidianPurpleLight,
                        indicatorColor = ObsidianPurpleContainer,
                        unselectedIconColor = ObsidianTextSecondary,
                        unselectedTextColor = ObsidianTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_search")
                )

                NavigationBarItem(
                    selected = activeTab == VaultTab.WIKI,
                    onClick = { viewModel.selectTab(VaultTab.WIKI) },
                    icon = {
                        Icon(
                            if (activeTab == VaultTab.WIKI) Icons.Default.Psychology else Icons.Outlined.Psychology,
                            contentDescription = "LLM Wiki"
                        )
                    },
                    label = { Text("LLM Wiki", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ObsidianPurpleLight,
                        selectedTextColor = ObsidianPurpleLight,
                        indicatorColor = ObsidianPurpleContainer,
                        unselectedIconColor = ObsidianTextSecondary,
                        unselectedTextColor = ObsidianTextSecondary
                    ),
                    modifier = Modifier.testTag("nav_wiki")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                VaultTab.EXPLORER -> {
                    ExplorerView(
                        notes = allNotes,
                        bookmarkedNotes = bookmarkedNotes,
                        allTags = allTags,
                        allFolders = allFolders,
                        isSyncing = isSyncing,
                        syncMessage = syncMessage,
                        selectedTagFilter = selectedTagFilter,
                        vaultPath = viewModel.vaultAbsolutePath,
                        onNoteClick = { viewModel.openNote(it) },
                        onCreateNote = { title, folder -> viewModel.createNote(title, folder) },
                        onCreateFolder = { viewModel.createFolder(it) },
                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                        onSyncFilesystem = { viewModel.syncFilesystem() },
                        onTagSelected = { viewModel.setTagFilter(it) }
                    )
                }
                VaultTab.EDITOR -> {
                    EditorView(
                        note = activeNote,
                        isLivePreview = isLivePreview,
                        showBacklinks = showBacklinks,
                        linkedMentions = linkedMentions,
                        unlinkedMentions = unlinkedMentions,
                        onContentChanged = { viewModel.saveActiveNote(it) },
                        onTogglePreview = { viewModel.toggleLivePreview() },
                        onToggleBacklinks = { viewModel.toggleBacklinks() },
                        onWikilinkClicked = { viewModel.openNoteByTitle(it) },
                        onSuggestLinks = { viewModel.suggestLinksForActiveNote() },
                        onToggleBookmark = { viewModel.toggleBookmark(it) },
                        onDeleteNote = { viewModel.deleteActiveNote() },
                        onOpenMentionNote = { viewModel.openNote(it) }
                    )
                }
                VaultTab.GRAPH -> {
                    GraphView(
                        graphData = graphData,
                        allNotes = allNotes,
                        onOpenNote = { viewModel.openNote(it) }
                    )
                }
                VaultTab.SEARCH -> {
                    SearchView(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        allTags = allTags,
                        selectedTagFilter = selectedTagFilter,
                        onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                        onTagSelected = { viewModel.setTagFilter(it) },
                        onOpenNote = { viewModel.openNote(it) }
                    )
                }
                VaultTab.WIKI -> {
                    WikiChatView(
                        messages = chatMessages,
                        isLoading = isChatLoading,
                        selectedModel = chatModel,
                        onModelSelected = { viewModel.setChatModel(it) },
                        onSendMessage = { text, askVault -> viewModel.sendChatMessage(text, askVault) },
                        onSynthesizeTopic = { viewModel.synthesizeWikiTopic(it) },
                        onWikilinkClicked = { viewModel.openNoteByTitle(it) },
                        onSaveToVault = { viewModel.saveMessageAsNote(it) },
                        onRunDiagnostic = { viewModel.triggerSystemDiagnostic() },
                        onOpenSkills = {
                            viewModel.refreshDeviceTelemetry()
                            viewModel.loadStorageAudit()
                            showAndroidSkillsDialog = true
                        }
                    )
                }
            }
        }
    }

    if (showAndroidSkillsDialog) {
        AndroidSkillsDialog(
            skillsCatalog = viewModel.skillsCatalog,
            telemetry = deviceTelemetry,
            storageAudit = storageAudit,
            onRefreshTelemetry = {
                viewModel.refreshDeviceTelemetry()
                viewModel.loadStorageAudit()
            },
            onExecuteHook = { hookName ->
                viewModel.executeAutomationHook(hookName)
                showAndroidSkillsDialog = false
                viewModel.selectTab(VaultTab.WIKI)
            },
            onTestToast = { msg -> viewModel.showToast(msg) },
            onTestHaptic = { viewModel.triggerHaptic(60) },
            onDismiss = { showAndroidSkillsDialog = false }
        )
    }
}
