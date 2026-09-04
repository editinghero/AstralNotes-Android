package com.astralquarks.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.model.NoteColorPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IMG_REGEX = Regex("!\\[.*?\\]\\((https?://.*?)\\)")
private val TASK_LINE_REGEX = Regex("^[\\-*+]\\s+\\[[ xX]\\].*")
private val TASK_CHECKED_REGEX = Regex("^[\\-*+]\\s+\\[[xX]\\].*")
private val TASK_PREFIX_REGEX = Regex("^[\\-*+]\\s+\\[[ xX]\\]\\s*")
private val MD_FORMAT_REGEX = Regex("(#+\\s+|\\[(.*?)\\]\\(.*?\\)|!\\[.*?\\]\\(.*?\\)|[*_~=`\"])")
private val STRIP_HEADING = Regex("^#{1,6}\\s+", RegexOption.MULTILINE)
private val STRIP_BOLD_STAR = Regex("\\*\\*(.+?)\\*\\*")
private val STRIP_BOLD_UND = Regex("__(.+?)__")
private val STRIP_ITALIC_STAR = Regex("\\*(.+?)\\*")
private val STRIP_ITALIC_UND = Regex("_(.+?)_")
private val STRIP_STRIKE = Regex("~~(.+?)~~")
private val STRIP_HIGHLIGHT = Regex("==(.+?)==")
private val STRIP_CODE = Regex("`(.+?)`")
private val STRIP_IMG = Regex("!\\[.*?\\]\\(.*?\\)")
private val STRIP_LINK = Regex("\\[(.+?)\\]\\(.*?\\)")
private val STRIP_QUOTE = Regex("^[>]+\\s?", RegexOption.MULTILINE)
private val STRIP_TASK = Regex("^[-*+]\\s+\\[[ xX]\\]\\s*", RegexOption.MULTILINE)
private val STRIP_BULLET = Regex("^[-*+]\\s+", RegexOption.MULTILINE)
private val STRIP_NUMBERED = Regex("^\\d+\\.\\s+", RegexOption.MULTILINE)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    onToggleArchive: (() -> Unit)? = null,
    onMoveToTrash: (() -> Unit)? = null,
    onRestoreFromTrash: (() -> Unit)? = null,
    onPermanentlyDelete: (() -> Unit)? = null,
    onToggleLock: (() -> Unit)? = null,
    onChangeColor: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isTrashSection: Boolean = false,
    isLockedSection: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val cardBg = remember(note.colorHex, colorScheme) {
        NoteColorPalette.getNoteContainerColor(note.colorHex, colorScheme)
    }
    val primaryTextColor = remember(cardBg) { NoteColorPalette.getNoteTextColor(cardBg) }
    val secondaryTextColor = remember(cardBg) { NoteColorPalette.getNoteSecondaryTextColor(cardBg) }
    var showMenu by remember { mutableStateOf(false) }

    // Check for image URLs in content or imageUrls list
    val firstImageUrl = remember(note.imageUrls, note.content) {
        note.imageUrls.firstOrNull() ?: IMG_REGEX.find(note.content)?.groupValues?.get(1)
    }

    // Extract checklist lines for preview if any
    val checklistItems = remember(note.content) {
        if (!note.content.contains("[")) emptyList()
        else {
            note.content.lines()
                .filter { it.trim().matches(TASK_LINE_REGEX) }
                .take(3)
                .map { line ->
                    val checked = line.matches(TASK_CHECKED_REGEX)
                    val text = line.replaceFirst(TASK_PREFIX_REGEX, "")
                    checked to text
                }
        }
    }


    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "card_scale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().testTag("note_card_${note.id}").graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp)

    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Optional image header
            if (!firstImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = firstImageUrl,
                    contentDescription = "Note preview image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
            }

            Column(modifier = Modifier.padding(14.dp)) {
                // Header with Title and Pin button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (note.title.isNotBlank()) {
                        Text(
                            text = note.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = primaryTextColor
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    if (!isTrashSection && onTogglePin != null) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("pin_note_${note.id}")
                        ) {
                            Icon(
                                imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (note.isPinned) "Unpin" else "Pin",
                                tint = if (note.isPinned) MaterialTheme.colorScheme.primary else secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isLockedSection) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Note",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Checklist preview if present
                if (checklistItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        checklistItems.forEach { (checked, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (checked) MaterialTheme.colorScheme.primary else secondaryTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = text,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (checked) secondaryTextColor.copy(alpha = 0.5f) else primaryTextColor,
                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val plainPreview = remember(note.content) {
                        note.content
                            .replace(STRIP_HEADING, "")
                            .replace(STRIP_BOLD_STAR, "$1")
                            .replace(STRIP_BOLD_UND, "$1")
                            .replace(STRIP_ITALIC_STAR, "$1")
                            .replace(STRIP_ITALIC_UND, "$1")
                            .replace(STRIP_STRIKE, "$1")
                            .replace(STRIP_HIGHLIGHT, "$1")
                            .replace(STRIP_CODE, "$1")
                            .replace(STRIP_IMG, "")
                            .replace(STRIP_LINK, "$1")
                            .replace(STRIP_QUOTE, "")
                            .replace(STRIP_TASK, "")
                            .replace(STRIP_BULLET, "")
                            .replace(STRIP_NUMBERED, "")
                            .trim()
                            .take(200)
                    }
                    Box(modifier = Modifier.heightIn(max = 100.dp).clipToBounds()) {
                        Text(
                            text = plainPreview,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = secondaryTextColor,
                                lineHeight = 20.sp
                            ),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Tags flow row
                if (note.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        note.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (cardBg.luminance() > 0.45f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = secondaryTextColor,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Footer with Date and Context Menu
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateFormatted = remember(note.updatedAt) {
                        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                        sdf.format(Date(note.updatedAt))
                    }

                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = secondaryTextColor.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp).testTag("note_menu_${note.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp
                        ) {
                            if (!isTrashSection) {
                                if (onShare != null) {
                                    DropdownMenuItem(
                                        text = { Text("Share Note") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onShare()
                                        }
                                    )
                                }
                                if (onChangeColor != null) {
                                    DropdownMenuItem(
                                        text = { Text("Color") },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onChangeColor()
                                        }
                                    )
                                }
                                if (onToggleLock != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (note.isLocked) "Unlock to Public" else "Move to Vault") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onToggleLock()
                                        }
                                    )
                                }
                                if (onToggleArchive != null) {
                                    DropdownMenuItem(
                                        text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                                        leadingIcon = {
                                            Icon(
                                                if (note.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            onToggleArchive()
                                        }
                                    )
                                }
                                if (onMoveToTrash != null) {
                                    DropdownMenuItem(
                                        text = { Text("Move to Trash") },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onMoveToTrash()
                                        }
                                    )
                                }
                            } else {
                                if (onRestoreFromTrash != null) {
                                    DropdownMenuItem(
                                        text = { Text("Restore") },
                                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onRestoreFromTrash()
                                        }
                                    )
                                }
                                if (onPermanentlyDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Delete Forever", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            showMenu = false
                                            onPermanentlyDelete()
                                        }
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
