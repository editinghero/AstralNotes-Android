package com.astralquarks.notes.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Interactive Live Markdown Editor with immediate real-time synchronization,
 * auto-list continuation, instant in-place checklist item toggling, and rich preview.
 */
@Composable
fun InteractiveMarkdownEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    secondaryTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showInteractiveChecklistOverlay by remember { mutableStateOf(false) }

    // Check if the current note has checklist items
    val hasChecklistItems = remember(value.text) {
        value.text.contains("- [ ]") || value.text.contains("- [x]") || value.text.contains("- [X]")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("interactive_markdown_editor")
    ) {
        // Quick Markdown Formatting & Action Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarkdownToolButton(
                icon = Icons.Default.CheckBox,
                label = "Todo",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val insertion = if (currentText.isEmpty() || currentText.endsWith("\n")) "- [ ] " else "\n- [ ] "
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.FormatListBulleted,
                label = "Bullet",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val insertion = if (currentText.isEmpty() || currentText.endsWith("\n")) "- " else "\n- "
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.FormatListNumbered,
                label = "Number",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val insertion = if (currentText.isEmpty() || currentText.endsWith("\n")) "1. " else "\n1. "
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.Title,
                label = "Heading",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val insertion = if (currentText.isEmpty() || currentText.endsWith("\n")) "## " else "\n## "
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.FormatBold,
                label = "Bold",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val selectedText = if (sel.start != sel.end) currentText.substring(sel.start, sel.end) else "bold text"
                    val insertion = "**$selectedText**"
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.FormatItalic,
                label = "Italic",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val selectedText = if (sel.start != sel.end) currentText.substring(sel.start, sel.end) else "italic text"
                    val insertion = "*$selectedText*"
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.FormatQuote,
                label = "Quote",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val insertion = if (currentText.isEmpty() || currentText.endsWith("\n")) "> " else "\n> "
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            MarkdownToolButton(
                icon = Icons.Default.Code,
                label = "Code",
                onClick = {
                    val currentText = value.text
                    val sel = value.selection
                    val selectedText = if (sel.start != sel.end) currentText.substring(sel.start, sel.end) else "code"
                    val insertion = "`$selectedText`"
                    val newText = currentText.substring(0, sel.start) + insertion + currentText.substring(sel.end)
                    val newCursor = sel.start + insertion.length
                    onValueChange(TextFieldValue(newText, TextRange(newCursor)))
                }
            )

            if (hasChecklistItems) {
                AssistChip(
                    onClick = { showInteractiveChecklistOverlay = !showInteractiveChecklistOverlay },
                    label = {
                        Text(
                            if (showInteractiveChecklistOverlay) "Hide Checkboxes" else "Tap Checkboxes",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckBox,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Optional Interactive Checklist Quick-Tap Strip
        if (hasChecklistItems && showInteractiveChecklistOverlay) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Interactive Checklist (Tap to toggle):",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val lines = value.text.lines()
                    lines.forEachIndexed { lineIdx, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
                            val isChecked = trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")
                            val itemText = trimmed.substring(6)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        // Toggle this specific checklist item in the main text
                                        val newLines = lines.toMutableList()
                                        val indent = line.substring(0, line.indexOf("- ["))
                                        if (isChecked) {
                                            newLines[lineIdx] = "$indent- [ ] $itemText"
                                        } else {
                                            newLines[lineIdx] = "$indent- [x] $itemText"
                                        }
                                        val updatedFullText = newLines.joinToString("\n")
                                        onValueChange(TextFieldValue(updatedFullText, TextRange(updatedFullText.length)))
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = itemText.ifBlank { "Task" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isChecked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Markdown Input Field with Instant Synchronous Save & Auto-Continuations
        BasicTextField(
            value = value,
            onValueChange = { incoming ->
                // Apply auto list continuation and immediately notify parent
                val handled = MarkdownAutoListHelper.handleTextChange(value, incoming)
                onValueChange(handled)
            },
            textStyle = TextStyle(
                fontSize = 16.sp,
                lineHeight = 25.sp,
                fontFamily = FontFamily.Default,
                color = textColor
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp)
                        .padding(vertical = 8.dp)
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "Start writing note...\n• Full Markdown supported: # Headings, **bold**, *italic*\n• Auto-lists: `- [ ] ` checklist, `- ` bullet, `1. ` numbered, `> ` quotes\n• Images: ![Caption](https://url) or upload to Catbox",
                            style = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = secondaryTextColor.copy(alpha = 0.6f)
                            )
                        )
                    }
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("note_content_input")
        )
    }
}

@Composable
private fun MarkdownToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
