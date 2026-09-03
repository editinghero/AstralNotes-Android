package com.astralquarks.notes.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralquarks.notes.ai.ChatMessage
import com.astralquarks.notes.ai.GeminiActionParser
import com.astralquarks.notes.ai.GeminiManager
import com.astralquarks.notes.ai.GeminiNoteAction
import com.astralquarks.notes.ai.ParsedAiMessage
import com.astralquarks.notes.markdown.MarkdownRenderer
import com.astralquarks.notes.model.AiChatMessageEntity
import com.astralquarks.notes.model.Note
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SheetTabType {
    ACTIONS,
    CHAT,
    HISTORY,
    SETTINGS
}

private data class SheetTab(val type: SheetTabType, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantBottomSheet(
    note: Note?,
    allNotes: List<Note> = emptyList(),
    persistedMessages: List<AiChatMessageEntity> = emptyList(),
    onSaveChatMessage: ((isUser: Boolean, text: String) -> Unit)? = null,
    onClearChatHistory: (() -> Unit)? = null,
    geminiManager: GeminiManager,
    onDismiss: () -> Unit,
    onInsertTextIntoNote: ((String) -> Unit)? = null,
    onUpdateNote: ((title: String?, content: String?) -> Unit)? = null,
    onCreateNewNote: ((title: String, content: String) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isGlobalMode = note == null

    // Determine available tabs based on context
    // Global mode: Chat, History, Settings (No tools tab when outside specific notes)
    // Note mode: Actions, Chat, History, Settings
    val availableTabs = remember(isGlobalMode) {
        if (isGlobalMode) {
            listOf(
                SheetTab(SheetTabType.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
                SheetTab(SheetTabType.HISTORY, "History", Icons.Default.History),
                SheetTab(SheetTabType.SETTINGS, "Settings", Icons.Default.Tune)
            )
        } else {
            listOf(
                SheetTab(SheetTabType.ACTIONS, "Actions", Icons.Default.AutoAwesome),
                SheetTab(SheetTabType.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
                SheetTab(SheetTabType.HISTORY, "History", Icons.Default.History),
                SheetTab(SheetTabType.SETTINGS, "Settings", Icons.Default.Tune)
            )
        }
    }

    var selectedTab by remember { mutableStateOf(if (isGlobalMode) SheetTabType.CHAT else SheetTabType.ACTIONS) }
    var isLoading by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // In-memory chat messages combined with DB-backed history
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }
    var chatInput by remember { mutableStateOf("") }

    // Global AI note targeting state
    val targetedNoteIds = remember { mutableStateListOf<String>() }
    var showNotePickerForTargeting by remember { mutableStateOf(false) }

    // History search filter
    var historyFilterQuery by remember { mutableStateOf("") }

    // Sync persisted DB messages into UI state
    LaunchedEffect(persistedMessages) {
        if (persistedMessages.isNotEmpty() && chatMessages.isEmpty()) {
            chatMessages.clear()
            persistedMessages.forEach { entity ->
                chatMessages.add(
                    ChatMessage(
                        role = if (entity.isUser) "user" else "model",
                        content = entity.text,
                        timestamp = entity.timestamp
                    )
                )
            }
        }
    }

    // Settings state
    var customApiKeyInput by remember { mutableStateOf(geminiManager.customApiKey) }
    var customModelInput by remember { mutableStateOf(geminiManager.customModelName) }
    var isThinkingEnabled by remember { mutableStateOf(geminiManager.isThinkingModeEnabled) }
    var isSearchEnabled by remember { mutableStateOf(geminiManager.isSearchGroundingEnabled) }

    // Pulse animation for thinking state
    val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.testTag("ai_assistant_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Gemini Note Intelligence",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = if (note != null) "Note: ${note.title.ifBlank { "Untitled" }}" else "Global Assistant (${allNotes.size} notes indexed)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Done")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Modern Material 3 Segmented Switcher
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableTabs.forEach { tab ->
                        val isSelected = selectedTab == tab.type
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedTab = tab.type },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            tonalElevation = if (isSelected) 3.dp else 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Render current view depending on selected tab
            when (selectedTab) {
                SheetTabType.ACTIONS -> {
                    // NOTE MODE: Instant AI Actions Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = "Instant Note Actions",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = false,
                                shape = RoundedCornerShape(20.dp),
                                onClick = {
                                    if (note != null) {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            resultText = null
                                            val res = geminiManager.summarizeNote(note)
                                            isLoading = false
                                            res.onSuccess { resultText = it }
                                            res.onFailure { errorMessage = it.localizedMessage }
                                        }
                                    }
                                },
                                label = { Text("Summarize") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("ai_summarize_chip")
                            )

                            FilterChip(
                                selected = false,
                                shape = RoundedCornerShape(20.dp),
                                onClick = {
                                    if (note != null) {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            resultText = null
                                            val res = geminiManager.generateActionChecklist(note)
                                            isLoading = false
                                            res.onSuccess { resultText = it }
                                            res.onFailure { errorMessage = it.localizedMessage }
                                        }
                                    }
                                },
                                label = { Text("Action Items") },
                                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("ai_tasks_chip")
                            )

                            FilterChip(
                                selected = false,
                                shape = RoundedCornerShape(20.dp),
                                onClick = {
                                    if (note != null) {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            resultText = null
                                            val res = geminiManager.polishMarkdownNote(note)
                                            isLoading = false
                                            res.onSuccess { resultText = it }
                                            res.onFailure { errorMessage = it.localizedMessage }
                                        }
                                    }
                                },
                                label = { Text("Polish Note") },
                                leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("ai_polish_chip")
                            )

                            FilterChip(
                                selected = false,
                                shape = RoundedCornerShape(20.dp),
                                onClick = {
                                    if (note != null) {
                                        scope.launch {
                                            isLoading = true
                                            errorMessage = null
                                            resultText = null
                                            val res = geminiManager.brainstormIdeas(note)
                                            isLoading = false
                                            res.onSuccess { resultText = it }
                                            res.onFailure { errorMessage = it.localizedMessage }
                                        }
                                    }
                                },
                                label = { Text("Brainstorm") },
                                leadingIcon = { Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("ai_brainstorm_chip")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.alpha(pulseAlpha)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (isThinkingEnabled) "Gemini is performing deep reasoning..." else "Gemini is generating response...",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        } else if (errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Operation Error",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer)
                                    )
                                }
                            }
                        } else if (resultText != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Gemini Response",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Gemini Output", resultText!!)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                            }

                                            if (onUpdateNote != null) {
                                                FilledTonalButton(
                                                    onClick = {
                                                        onUpdateNote.invoke(null, resultText!!)
                                                        Toast.makeText(context, "Note Updated", Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    },
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Text("Replace Note")
                                                }
                                            }

                                            if (onInsertTextIntoNote != null) {
                                                Button(
                                                    onClick = {
                                                        onInsertTextIntoNote.invoke(resultText!!)
                                                        Toast.makeText(context, "Inserted into note", Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    },
                                                    shape = RoundedCornerShape(14.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Input, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Insert")
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                        item {
                                            MarkdownRenderer(markdown = resultText!!)
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Select an action chip above to run instant Gemini operations on this note.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                SheetTabType.CHAT -> {
                    // CHAT TAB (Global Chat or Note-Specific Chat)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // In Global Chat, show selective note targeting bar
                        if (isGlobalMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = targetedNoteIds.isEmpty(),
                                    onClick = { targetedNoteIds.clear() },
                                    label = { Text("Smart Auto-Search (Active)") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                FilterChip(
                                    selected = targetedNoteIds.isNotEmpty(),
                                    onClick = { showNotePickerForTargeting = true },
                                    label = {
                                        Text(if (targetedNoteIds.isEmpty()) "+ Focus Specific Note" else "Targeting ${targetedNoteIds.size} notes")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                    shape = RoundedCornerShape(16.dp)
                                )

                                if (targetedNoteIds.isNotEmpty()) {
                                    IconButton(
                                        onClick = { targetedNoteIds.clear() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear targeting", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Quick Prompts if chat is empty
                        if (chatMessages.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val suggestions = if (isGlobalMode) {
                                    listOf(
                                        "Summarize my recent notes",
                                        "Find all tasks and checklist items",
                                        "What ideas did I write down recently?",
                                        "Create a weekly goals note"
                                    )
                                } else {
                                    listOf(
                                        "Summarize the key takeaways",
                                        "Extract all next action steps",
                                        "Check for missing details or gaps",
                                        "Restructure into clean markdown"
                                    )
                                }
                                suggestions.forEach { prompt ->
                                    FilterChip(
                                        selected = false,
                                        shape = RoundedCornerShape(20.dp),
                                        onClick = { chatInput = prompt },
                                        label = { Text(prompt, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (chatMessages.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (isGlobalMode) "Global Knowledge Assistant" else "Conversational Note Assistant",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = if (isGlobalMode) {
                                                    "Ask questions across your notes with on-demand selective retrieval."
                                                } else {
                                                    "Ask questions, brainstorm expansions, or edit this note collaboratively."
                                                },
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }

                            items(chatMessages, key = { msg -> "${msg.timestamp}_${msg.role}" }) { msg ->
                                val isUser = msg.role == "user"
                                val parsed = remember(msg.content) {
                                    if (!isUser) GeminiActionParser.parse(msg.content) else ParsedAiMessage(msg.content, null)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = if (isUser) {
                                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 6.dp)
                                        } else {
                                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 24.dp)
                                        },
                                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        tonalElevation = if (isUser) 0.dp else 2.dp,
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            if (!isUser) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Gemini",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        IconButton(
                                                            onClick = {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                                val clip = ClipData.newPlainText("Gemini Message", parsed.displayText)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ContentCopy,
                                                                contentDescription = "Copy message",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }

                                                        if (!isGlobalMode && onInsertTextIntoNote != null) {
                                                            IconButton(
                                                                onClick = {
                                                                    onInsertTextIntoNote.invoke(parsed.displayText)
                                                                    Toast.makeText(context, "Inserted into note", Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.Input,
                                                                    contentDescription = "Insert into note",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                MarkdownRenderer(markdown = parsed.displayText)

                                                // If Gemini suggested an actionable operation, show interactive action card
                                                val action = parsed.action
                                                if (action != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 10.dp)
                                                    ) {
                                                        Column(modifier = Modifier.padding(10.dp)) {
                                                            when (action) {
                                                                is GeminiNoteAction.CreateNote -> {
                                                                    Text(
                                                                        text = "Action: Create New Note",
                                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                                    )
                                                                    Text(
                                                                        text = "Title: ${action.title}",
                                                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                    )
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Button(
                                                                        onClick = {
                                                                            onCreateNewNote?.invoke(action.title, action.content)
                                                                            Toast.makeText(context, "Note Created", Toast.LENGTH_SHORT).show()
                                                                            onDismiss()
                                                                        },
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Create Note")
                                                                    }
                                                                }
                                                                is GeminiNoteAction.UpdateNote -> {
                                                                    Text(
                                                                        text = "Action: Update Note Content",
                                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                                    )
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Button(
                                                                        onClick = {
                                                                            onUpdateNote?.invoke(action.title, action.content)
                                                                            Toast.makeText(context, "Note Updated", Toast.LENGTH_SHORT).show()
                                                                            onDismiss()
                                                                        },
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Apply Changes")
                                                                    }
                                                                }
                                                                is GeminiNoteAction.AppendToNote -> {
                                                                    Text(
                                                                        text = "Action: Append Items",
                                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                                    )
                                                                    Spacer(modifier = Modifier.height(6.dp))
                                                                    Button(
                                                                        onClick = {
                                                                            onInsertTextIntoNote?.invoke(action.content)
                                                                            Toast.makeText(context, "Items Added", Toast.LENGTH_SHORT).show()
                                                                            onDismiss()
                                                                        },
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Add Checklist to Note")
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = msg.content,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (isLoading) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.alpha(pulseAlpha)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = if (isThinkingEnabled) "Gemini is reasoning..." else "Gemini is replying...",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pill Composer
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = chatInput,
                                    onValueChange = { chatInput = it },
                                    placeholder = { Text(if (isGlobalMode) "Ask across your notes..." else "Ask about this note...") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("ai_chat_input"),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            val query = chatInput.trim()
                                            if (query.isNotBlank() && !isLoading) {
                                                chatMessages.add(ChatMessage("user", query))
                                                onSaveChatMessage?.invoke(true, query)
                                                chatInput = ""
                                                scope.launch {
                                                    isLoading = true
                                                    val res = if (note != null) {
                                                        geminiManager.chatWithNote(note, query, chatMessages)
                                                    } else {
                                                        geminiManager.askAcrossAllNotes(
                                                            notes = allNotes,
                                                            userQuestion = query,
                                                            chatHistory = chatMessages,
                                                            explicitTargetNoteIds = targetedNoteIds.toSet()
                                                        )
                                                    }
                                                    isLoading = false
                                                    res.onSuccess { reply ->
                                                        chatMessages.add(ChatMessage("model", reply))
                                                        onSaveChatMessage?.invoke(false, reply)
                                                    }
                                                    res.onFailure { err ->
                                                        val errTxt = "Error: ${err.localizedMessage}"
                                                        chatMessages.add(ChatMessage("model", errTxt))
                                                        onSaveChatMessage?.invoke(false, errTxt)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        val query = chatInput.trim()
                                        if (query.isNotBlank() && !isLoading) {
                                            chatMessages.add(ChatMessage("user", query))
                                            onSaveChatMessage?.invoke(true, query)
                                            chatInput = ""
                                            scope.launch {
                                                isLoading = true
                                                val res = if (note != null) {
                                                    geminiManager.chatWithNote(note, query, chatMessages)
                                                } else {
                                                    geminiManager.askAcrossAllNotes(
                                                        notes = allNotes,
                                                        userQuestion = query,
                                                        chatHistory = chatMessages,
                                                        explicitTargetNoteIds = targetedNoteIds.toSet()
                                                    )
                                                }
                                                isLoading = false
                                                res.onSuccess { reply ->
                                                    chatMessages.add(ChatMessage("model", reply))
                                                    onSaveChatMessage?.invoke(false, reply)
                                                }
                                                res.onFailure { err ->
                                                    val errTxt = "Error: ${err.localizedMessage}"
                                                    chatMessages.add(ChatMessage("model", errTxt))
                                                    onSaveChatMessage?.invoke(false, errTxt)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .testTag("ai_send_chat_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                SheetTabType.HISTORY -> {
                    // CHAT HISTORY DEDICATED TAB
                    val filteredHistory = remember(persistedMessages, historyFilterQuery) {
                        if (historyFilterQuery.isBlank()) {
                            persistedMessages
                        } else {
                            persistedMessages.filter { it.text.contains(historyFilterQuery, ignoreCase = true) }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${persistedMessages.size} Saved Messages",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            if (persistedMessages.isNotEmpty() && onClearChatHistory != null) {
                                TextButton(
                                    onClick = { showClearConfirmDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All")
                                }
                            }
                        }

                        if (persistedMessages.size > 3) {
                            OutlinedTextField(
                                value = historyFilterQuery,
                                onValueChange = { historyFilterQuery = it },
                                placeholder = { Text("Search history...") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (historyFilterQuery.isNotEmpty()) {
                                        IconButton(onClick = { historyFilterQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )
                        }

                        if (filteredHistory.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = if (historyFilterQuery.isNotEmpty()) "No messages match your search." else "No Chat History Recorded",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = if (isGlobalMode) {
                                            "Global inquiries and answers with Gemini will be automatically saved here."
                                        } else {
                                            "Conversations specific to this note will be preserved here."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            val dateFormat = remember { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()) }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredHistory, key = { item -> item.id }) { item ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (item.isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.isUser) Icons.Default.Person else Icons.Default.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = if (item.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (item.isUser) "You" else "Gemini",
                                                        style = MaterialTheme.typography.labelMedium.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (item.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                                        )
                                                    )
                                                    Text(
                                                        text = "• ${dateFormat.format(Date(item.timestamp))}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                        )
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(
                                                        onClick = {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            val clip = ClipData.newPlainText("Chat Message", item.text)
                                                            clipboard.setPrimaryClip(clip)
                                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copy message",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }

                                                    if (!isGlobalMode && !item.isUser && onInsertTextIntoNote != null) {
                                                        IconButton(
                                                            onClick = {
                                                                onInsertTextIntoNote.invoke(item.text)
                                                                Toast.makeText(context, "Inserted into note", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.Input,
                                                                contentDescription = "Insert into note",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (item.isUser) {
                                                Text(
                                                    text = item.text,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            } else {
                                                MarkdownRenderer(markdown = item.text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                SheetTabType.SETTINGS -> {
                    // MODEL & KEYS TAB (Settings)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Gemini Configuration & Model Tuning",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = {
                                customApiKeyInput = it
                                geminiManager.customApiKey = it
                            },
                            label = { Text("Custom Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("custom_api_key_input"),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        OutlinedTextField(
                            value = customModelInput,
                            onValueChange = {
                                customModelInput = it
                                geminiManager.customModelName = it
                            },
                            label = { Text("Gemini Model Name") },
                            placeholder = { Text("gemini-3.7-flash") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("custom_model_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("gemini-3.7-flash", "gemini-3.5-flash", "gemini-3.1-pro-preview").forEach { mName ->
                                FilterChip(
                                    selected = customModelInput == mName,
                                    onClick = {
                                        customModelInput = mName
                                        geminiManager.customModelName = mName
                                    },
                                    label = { Text(mName) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Thinking Mode (High Reasoning)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Enables step-by-step reasoning for deep note synthesis",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                            Switch(
                                checked = isThinkingEnabled,
                                onCheckedChange = {
                                    isThinkingEnabled = it
                                    geminiManager.isThinkingModeEnabled = it
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Search Grounding",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Fact-check and enrich notes with real-time web knowledge",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                            Switch(
                                checked = isSearchEnabled,
                                onCheckedChange = {
                                    isSearchEnabled = it
                                    geminiManager.isSearchGroundingEnabled = it
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for selecting specific notes to target in Global AI Chat
    if (showNotePickerForTargeting) {
        AlertDialog(
            onDismissRequest = { showNotePickerForTargeting = false },
            title = { Text("Focus Specific Notes") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text(
                        text = "Select notes to feed directly into Gemini's attention context for this conversation:",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(allNotes, key = { n -> n.id }) { n ->
                            val isTargeted = targetedNoteIds.contains(n.id)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isTargeted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isTargeted) targetedNoteIds.remove(n.id) else targetedNoteIds.add(n.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isTargeted) Icons.Default.Check else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isTargeted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = n.title.ifBlank { "Untitled Note" },
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        if (n.content.isNotBlank()) {
                                            Text(
                                                text = n.content.take(60).replace("\n", " "),
                                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showNotePickerForTargeting = false }) {
                    Text("Apply (${targetedNoteIds.size} Selected)")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    targetedNoteIds.clear()
                    showNotePickerForTargeting = false
                }) {
                    Text("Clear Selection")
                }
            }
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Chat History") },
            text = { Text("Are you sure you want to clear the AI conversation history? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        chatMessages.clear()
                        onClearChatHistory?.invoke()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
