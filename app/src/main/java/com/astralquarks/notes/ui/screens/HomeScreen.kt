package com.astralquarks.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.model.NoteColorPalette
import com.astralquarks.notes.ui.components.ColorPickerDialog
import com.astralquarks.notes.ui.components.ExpressiveSearchBar
import com.astralquarks.notes.ui.components.NoteCard
import com.astralquarks.notes.ui.components.ShareNoteDialog
import com.astralquarks.notes.util.NoteExporter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    notes: List<Note>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onCreateNote: (String) -> Unit,
    onTogglePin: (Note) -> Unit,
    onToggleArchive: (Note) -> Unit,
    onMoveToTrash: (Note) -> Unit,
    onToggleLock: (Note) -> Unit,
    onUpdateNoteColor: (Note, String) -> Unit,
    onOpenAiSheet: () -> Unit,
    onProfileClick: () -> Unit,
    userPhotoUrl: String?,
    userDisplayName: String?,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isGridView by remember { mutableStateOf(true) }
    var noteForColorPicker by remember { mutableStateOf<Note?>(null) }
    var noteToShare by remember { mutableStateOf<Note?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Distinct tags with count
    val tagCounts = remember(notes) {
        val map = mutableMapOf<String, Int>()
        notes.forEach { note ->
            note.tags.forEach { tag ->
                if (tag.isNotBlank()) {
                    map[tag] = (map[tag] ?: 0) + 1
                }
            }
        }
        map.toList().sortedByDescending { it.second }
    }

    // Filter notes based on search query & multi-tag filter
    val filteredNotes = remember(notes, searchQuery, selectedTags) {
        notes.filter { note ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true) ||
                note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
            val matchesTags = if (selectedTags.isEmpty()) true else {
                selectedTags.any { tag -> note.tags.contains(tag) }
            }
            matchesQuery && matchesTags
        }
    }

    val pinnedNotes = remember(filteredNotes) { filteredNotes.filter { it.isPinned } }
    val otherNotes = remember(filteredNotes) { filteredNotes.filter { !it.isPinned } }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ExpressiveSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onOpenDrawer = onOpenDrawer,
                    isGridView = isGridView,
                    onToggleView = { isGridView = !isGridView },
                    userPhotoUrl = userPhotoUrl,
                    userDisplayName = userDisplayName,
                    isSyncing = isSyncing,
                    onProfileClick = onProfileClick
                )

                if (tagCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTags.isEmpty(),
                            onClick = { selectedTags = emptySet() },
                            label = { Text("All (${notes.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("filter_chip_all")
                        )

                        tagCounts.forEach { (tag, count) ->
                            val isSelected = selectedTags.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag
                                    } else {
                                        selectedTags + tag
                                    }
                                },
                                label = { Text("#$tag ($count)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("filter_chip_$tag")
                            )
                        }

                        if (selectedTags.isNotEmpty()) {
                            IconButton(
                                onClick = { selectedTags = emptySet() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear tag filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Material 3 Expressive Floating Bottom Dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Clickable "Take a note..." area
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onCreateNote("") }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Take a note...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.testTag("bottom_take_note_text")
                            )
                        }

                        // Quick action icons and Elevated Expressive FAB
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onCreateNote("- [ ] ") },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("quick_checklist_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = "New Checklist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { onCreateNote("![Image]()\n\n") },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("quick_image_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "New Image Note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onOpenAiSheet,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("quick_ai_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Assistant",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Material 3 Expressive Add FAB
                            FloatingActionButton(
                                onClick = { onCreateNote("") },
                                shape = RoundedCornerShape(24.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 2.dp
                                ),
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("fab_create_note")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create Note",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (filteredNotes.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Text(
                            text = if (searchQuery.isNotEmpty()) "No notes match \"$searchQuery\""
                            else if (selectedTags.isNotEmpty()) "No notes with selected tags"
                            else "No notes yet",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedTags.isNotEmpty()) "Try clearing search or tag filters"
                            else "Tap 'Take a note...' or '+' below to create your first note",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            } else if (isGridView) {
                // Grid layout (Staggered)
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_staggered_grid"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            SectionHeader(title = "PINNED (${pinnedNotes.size})")
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onTogglePin = { onTogglePin(note) },
                                onToggleArchive = { onToggleArchive(note) },
                                onMoveToTrash = { onMoveToTrash(note) },
                                onToggleLock = { onToggleLock(note) },
                                onChangeColor = { noteForColorPicker = note },
                                onShare = { noteToShare = note }
                            )
                        }
                    }

                    if (otherNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                SectionHeader(title = "OTHERS (${otherNotes.size})")
                            }
                        }
                        items(otherNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onTogglePin = { onTogglePin(note) },
                                onToggleArchive = { onToggleArchive(note) },
                                onMoveToTrash = { onMoveToTrash(note) },
                                onToggleLock = { onToggleLock(note) },
                                onChangeColor = { noteForColorPicker = note },
                                onShare = { noteToShare = note }
                            )
                        }
                    }

                    item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // Single Column / List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("notes_list_view"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item {
                            SectionHeader(title = "PINNED (${pinnedNotes.size})")
                        }
                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onTogglePin = { onTogglePin(note) },
                                onToggleArchive = { onToggleArchive(note) },
                                onMoveToTrash = { onMoveToTrash(note) },
                                onToggleLock = { onToggleLock(note) },
                                onChangeColor = { noteForColorPicker = note },
                                onShare = { noteToShare = note }
                            )
                        }
                    }

                    if (otherNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item {
                                SectionHeader(title = "OTHERS (${otherNotes.size})")
                            }
                        }
                        items(otherNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onTogglePin = { onTogglePin(note) },
                                onToggleArchive = { onToggleArchive(note) },
                                onMoveToTrash = { onMoveToTrash(note) },
                                onToggleLock = { onToggleLock(note) },
                                onChangeColor = { noteForColorPicker = note },
                                onShare = { noteToShare = note }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Color picker dialog
    if (noteForColorPicker != null) {
        val currentNote = noteForColorPicker!!
        ColorPickerDialog(
            currentColorHex = currentNote.colorHex,
            onColorSelected = { newColor ->
                onUpdateNoteColor(currentNote, newColor)
                noteForColorPicker = null
            },
            onDismiss = { noteForColorPicker = null }
        )
    }

    // Share Note Dialog
    if (noteToShare != null) {
        val target = noteToShare!!
        ShareNoteDialog(
            noteTitle = target.title,
            onSelectFormat = { format ->
                NoteExporter.shareNote(context, target, format)
            },
            onDismiss = { noteToShare = null }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 8.dp)
    )
}
