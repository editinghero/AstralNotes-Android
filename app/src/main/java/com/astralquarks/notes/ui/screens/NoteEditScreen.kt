package com.astralquarks.notes.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MenuBook
import com.astralquarks.notes.markdown.MarkdownRenderer
import com.astralquarks.notes.ui.components.InteractiveMarkdownEditor
import com.astralquarks.notes.ui.components.MarkdownAutoListHelper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralquarks.notes.ai.GeminiManager
import com.astralquarks.notes.markdown.MarkdownParser
import com.astralquarks.notes.markdown.MarkdownRenderer
import com.astralquarks.notes.markdown.MarkdownToolbar
import com.astralquarks.notes.model.AiChatMessageEntity
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.model.NoteColorPalette
import com.astralquarks.notes.ui.components.AiAssistantBottomSheet
import com.astralquarks.notes.ui.components.ColorPickerDialog
import com.astralquarks.notes.ui.components.ImageUrlDialog
import com.astralquarks.notes.ui.components.LinkDialog
import com.astralquarks.notes.ui.components.ShareNoteDialog
import com.astralquarks.notes.ui.theme.GeminiSparklePink
import com.astralquarks.notes.util.NoteExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    noteId: String?,
    initialContent: String = "",
    allExistingTags: List<String> = emptyList(),
    persistedMessages: List<AiChatMessageEntity> = emptyList(),
    onSaveChatMessage: ((isUser: Boolean, text: String) -> Unit)? = null,
    onClearChatHistory: (() -> Unit)? = null,
    getNote: suspend (String) -> Note?,
    onSaveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onBack: () -> Unit,
    geminiManager: GeminiManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var note by remember {
        mutableStateOf(
            Note(
                id = noteId ?: java.util.UUID.randomUUID().toString(),
                title = "",
                content = initialContent,
                colorHex = "#DEFAULT"
            )
        )
    }

    var titleValue by remember { mutableStateOf(TextFieldValue(note.title)) }
    var contentValue by remember { mutableStateOf(TextFieldValue(note.content)) }
    var isRawMode by remember { mutableStateOf(false) }

    val tagsList = remember { mutableStateListOf<String>() }
    var showTagDialog by remember { mutableStateOf(false) }
    var newTagInput by remember { mutableStateOf("") }

    var showColorPicker by remember { mutableStateOf(false) }
    var showImageUrlDialog by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showAiSheet by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var isExportingShare by remember { mutableStateOf(false) }
    var shareStatusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(noteId) {
        if (!noteId.isNullOrBlank()) {
            val existing = getNote(noteId)
            if (existing != null) {
                note = existing
                titleValue = TextFieldValue(existing.title, TextRange(existing.title.length))
                contentValue = TextFieldValue(existing.content, TextRange(existing.content.length))
                tagsList.clear()
                tagsList.addAll(existing.tags)
            }
        } else if (initialContent.isNotBlank()) {
            contentValue = TextFieldValue(initialContent, TextRange(initialContent.length))
        }
    }

    // Auto-save debouncer
    LaunchedEffect(note) {
        // Debounce database writes to prevent UI stutter during fast typing
        kotlinx.coroutines.delay(1200)
        onSaveNote(note)
    }

    // Auto-save helper (updates local state, LaunchedEffect handles the DB save)
    fun persistChanges() {
        val updated = note.copy(
            title = titleValue.text.trim(),
            content = contentValue.text,
            tags = tagsList.toList(),
            updatedAt = System.currentTimeMillis()
        )
        note = updated
    }

    // Guarantee that whenever the user leaves the screen (Back gesture, system back, navigation), changes are committed
    androidx.activity.compose.BackHandler {
        persistChanges()
        onSaveNote(note)
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Update timestamp one last time and save immediately
            val finalNote = note.copy(
                title = titleValue.text.trim(),
                content = contentValue.text,
                tags = tagsList.toList(),
                updatedAt = System.currentTimeMillis()
            )
            onSaveNote(finalNote)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val backgroundColor = NoteColorPalette.getNoteContainerColor(note.colorHex, colorScheme)
    val textColor = remember(backgroundColor) { NoteColorPalette.getNoteTextColor(backgroundColor) }
    val secondaryTextColor = remember(backgroundColor) { NoteColorPalette.getNoteSecondaryTextColor(backgroundColor) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .testTag("note_edit_screen"),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        onClick = {
                            persistChanges()
                            onBack()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                actions = {
                    // Top Corner AI Button (Google Gemini Integration)
                    if (!note.isLocked) { FilledTonalIconButton(
                        onClick = { showAiSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("top_corner_ai_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = GeminiSparklePink.copy(alpha = 0.15f),
                            contentColor = GeminiSparklePink
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI Assistant",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Pin toggle
                    IconButton(
                        onClick = {
                            val newPinned = !note.isPinned
                            note = note.copy(isPinned = newPinned)
                            persistChanges()
                        },
                        modifier = Modifier.testTag("pin_action_button")
                    ) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) MaterialTheme.colorScheme.primary else textColor
                        )
                    }

                    // Explicit Save Button
                    IconButton(
                        onClick = {
                            persistChanges()
                            onSaveNote(note)
                            Toast.makeText(context, "Note saved to database", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("save_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Note",
                            tint = textColor
                        )
                    }

                    // Color palette button
                    IconButton(
                        onClick = { showColorPicker = true },
                        modifier = Modifier.testTag("color_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Change Color",
                            tint = textColor
                        )
                    }

                    // Toggle Raw Markdown Editor Mode vs Formatted Markdown Preview Mode
                    IconButton(
                        onClick = { isRawMode = !isRawMode },
                        modifier = Modifier.testTag("toggle_raw_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isRawMode) Icons.Default.MenuBook else Icons.Default.Edit,
                            contentDescription = if (isRawMode) "Preview Markdown" else "Edit Markdown",
                            tint = textColor
                        )
                    }

                    // Share Note (MD, HTML, PDF)
                    IconButton(
                        onClick = {
                            persistChanges()
                            showShareDialog = true
                        },
                        modifier = Modifier.testTag("share_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Note",
                            tint = textColor
                        )
                    }

                    // Delete Note
                    IconButton(
                        onClick = {
                            onDeleteNote(note)
                            onBack()
                        },
                        modifier = Modifier.testTag("delete_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            if (isRawMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MarkdownToolbar(
                        onInsertText = { prefix, suffix, placeholder ->
                            val currentText = contentValue.text
                            val selection = contentValue.selection
                            val effectivePrefix = if (prefix == "1. ") {
                                val textBeforeCursor = currentText.substring(0, selection.start)
                                val lastLine = textBeforeCursor.lines().lastOrNull { it.isNotBlank() } ?: ""
                                val numMatch = Regex("^(\\s*)(\\d+)\\.\\s*").find(lastLine)
                                val nextNum = if (numMatch != null) (numMatch.groupValues[2].toIntOrNull() ?: 0) + 1 else 1
                                "$nextNum. "
                            } else prefix

                            val selectedText = if (selection.collapsed) placeholder else currentText.substring(selection.start, selection.end)
                            val newText = currentText.substring(0, selection.start) + effectivePrefix + selectedText + suffix + currentText.substring(selection.end)
                            val newCursor = selection.start + effectivePrefix.length + selectedText.length + suffix.length
                            contentValue = TextFieldValue(newText, TextRange(newCursor))
                            persistChanges()
                        },
                        onOpenImageDialog = { showImageUrlDialog = true },
                        onOpenLinkDialog = { showLinkDialog = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Note Title Input
            BasicTextField(
                value = titleValue,
                onValueChange = {
                    titleValue = it
                    persistChanges()
                },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (titleValue.text.isEmpty()) {
                        Text(
                            text = "Title",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = secondaryTextColor.copy(alpha = 0.6f)
                            )
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("note_title_input")
            )

            // Tags Flow
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tagsList.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { /* no-op */ },
                        label = { Text("#$tag", color = secondaryTextColor) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove tag",
                                tint = secondaryTextColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        tagsList.remove(tag)
                                        persistChanges()
                                    }
                            )
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (backgroundColor.luminance() > 0.45f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { showTagDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = secondaryTextColor, modifier = Modifier.size(14.dp))
                        Text(text = "Add Tag", color = secondaryTextColor, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Note Content: Toggle between Interactive Markdown Editor (raw/live) and Rich Formatted Markdown Preview
            if (isRawMode) {
                InteractiveMarkdownEditor(
                    value = contentValue,
                    onValueChange = { incoming ->
                        contentValue = incoming
                        persistChanges()
                    },
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color.Transparent
                ) {
                    if (contentValue.text.isBlank()) {
                        Text(
                            text = "Empty note. Tap here to write markdown...",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = secondaryTextColor.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .clickable { isRawMode = true }
                        )
                    } else {
                        MarkdownRenderer(
                            markdown = contentValue.text,
                            textColor = textColor,
                            onChecklistToggle = { taskItem ->
                                val updated = com.astralquarks.notes.markdown.MarkdownParser.toggleChecklist(contentValue.text, taskItem)
                                contentValue = TextFieldValue(updated, TextRange(updated.length))
                                persistChanges()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Timestamp footer
            val editedDate = remember(note.updatedAt) {
                val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                sdf.format(Date(note.updatedAt))
            }
            Text(
                text = "Edited $editedDate",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = secondaryTextColor.copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }

    // Dialogs
    if (showColorPicker) {
        ColorPickerDialog(
            currentColorHex = note.colorHex,
            onColorSelected = { hex ->
                note = note.copy(colorHex = hex)
                persistChanges()
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showImageUrlDialog) {
        ImageUrlDialog(
            onDismiss = { showImageUrlDialog = false },
            onInsertImage = { url, alt ->
                val imgMarkdown = "\n![$alt]($url)\n"
                val currentText = contentValue.text
                val selection = contentValue.selection
                val newText = currentText.substring(0, selection.start) + imgMarkdown + currentText.substring(selection.end)
                contentValue = TextFieldValue(newText, TextRange(selection.start + imgMarkdown.length))
                persistChanges()
                showImageUrlDialog = false
            }
        )
    }

    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onInsertLink = { text, url ->
                val linkMarkdown = "[$text]($url)"
                val currentText = contentValue.text
                val selection = contentValue.selection
                val newText = currentText.substring(0, selection.start) + linkMarkdown + currentText.substring(selection.end)
                contentValue = TextFieldValue(newText, TextRange(selection.start + linkMarkdown.length))
                persistChanges()
                showLinkDialog = false
            }
        )
    }

    if (showTagDialog) {
        val suggestedTags = remember(allExistingTags, tagsList) {
            (allExistingTags + listOf("Work", "Personal", "Ideas", "Tasks", "Project", "Markdown", "Finance", "Study"))
                .distinct()
                .filter { !tagsList.contains(it) }
        }

        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Add Tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        label = { Text("Tag Name") },
                        placeholder = { Text("e.g. project, urgent...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("new_tag_input")
                    )

                    if (suggestedTags.isNotEmpty()) {
                        Text(
                            text = "Suggestions & Existing Tags:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            suggestedTags.take(8).forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable {
                                        if (!tagsList.contains(tag)) {
                                            tagsList.add(tag)
                                            persistChanges()
                                        }
                                        showTagDialog = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Text(text = "#$tag", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newTagInput.trim().replace("#", "")
                        if (clean.isNotBlank() && !tagsList.contains(clean)) {
                            tagsList.add(clean)
                            persistChanges()
                        }
                        newTagInput = ""
                        showTagDialog = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAiSheet) {
        AiAssistantBottomSheet(
            note = note.copy(title = titleValue.text, content = contentValue.text),
            persistedMessages = persistedMessages,
            onSaveChatMessage = onSaveChatMessage,
            onClearChatHistory = onClearChatHistory,
            geminiManager = geminiManager,
            onDismiss = { showAiSheet = false },
            onInsertTextIntoNote = { aiText ->
                val currentText = contentValue.text
                val newText = if (currentText.isBlank()) aiText else "$currentText\n\n$aiText"
                contentValue = TextFieldValue(newText, TextRange(newText.length))
                persistChanges()
            },
            onUpdateNote = { newTitle, newContent ->
                if (!newTitle.isNullOrBlank()) {
                    titleValue = TextFieldValue(newTitle, TextRange(newTitle.length))
                }
                if (newContent != null) {
                    contentValue = TextFieldValue(newContent, TextRange(newContent.length))
                }
                persistChanges()
            },
            onCreateNewNote = { newTitle, newContent ->
                val created = Note(
                    id = java.util.UUID.randomUUID().toString(),
                    title = newTitle,
                    content = newContent,
                    colorHex = "#DEFAULT",
                    updatedAt = System.currentTimeMillis()
                )
                onSaveNote(created)
            }
        )
    }

    if (showShareDialog) {
        val currentSnapshot = note.copy(
            title = titleValue.text.trim(),
            content = contentValue.text,
            tags = tagsList.toList(),
            updatedAt = System.currentTimeMillis()
        )
        ShareNoteDialog(
            note = currentSnapshot,
            isExporting = isExportingShare,
            exportStatus = shareStatusText,
            onSelectFormat = { format ->
                isExportingShare = true
                shareStatusText = "Preparing share..."
                NoteExporter.shareNote(
                    context = context,
                    note = currentSnapshot,
                    format = format,
                    onStatusChange = { status ->
                        shareStatusText = status
                    },
                    onComplete = {
                        isExportingShare = false
                        shareStatusText = null
                        showShareDialog = false
                    },
                    onError = {
                        isExportingShare = false
                        shareStatusText = null
                    }
                )
            },
            onDismiss = {
                if (!isExportingShare) {
                    showShareDialog = false
                }
            }
        )
    }
}
