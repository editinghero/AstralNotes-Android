package com.astralquarks.notes.markdown

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage

@Composable
fun MarkdownRenderer(
    markdown: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    isSnippetPreview: Boolean = false,
    onChecklistToggle: ((TaskItem) -> Unit)? = null
) {
    val contentToParse = remember(markdown, isSnippetPreview) {
        if (isSnippetPreview && markdown.length > 300) {
            markdown.take(300)
        } else {
            markdown
        }
    }
    val blocks = remember(contentToParse) { MarkdownParser.parse(contentToParse) }
    val displayBlocks = if (isSnippetPreview) blocks.take(4) else blocks

    CompositionLocalProvider(LocalContentColor provides textColor) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isSnippetPreview) 4.dp else 10.dp)
        ) {
            displayBlocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> HeadingBlockView(block, textColor, isSnippetPreview)
                    is MarkdownBlock.Paragraph -> ParagraphBlockView(block.text, textColor, isSnippetPreview)
                    is MarkdownBlock.Blockquote -> if (!isSnippetPreview) BlockquoteBlockView(block) else Text(text = block.lines.joinToString(" "), style = MaterialTheme.typography.bodyMedium.copy(color = textColor), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    is MarkdownBlock.CodeBlock -> if (!isSnippetPreview) CodeBlockView(block) else Text(text = block.code, style = MaterialTheme.typography.bodySmall.copy(color = textColor, fontFamily = FontFamily.Monospace), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    is MarkdownBlock.BulletList -> BulletListView(block.items, textColor)
                    is MarkdownBlock.NumberedList -> NumberedListView(block.items, textColor)
                    is MarkdownBlock.TaskList -> TaskListView(block.items, onChecklistToggle, textColor)
                    is MarkdownBlock.Table -> if (!isSnippetPreview) TableBlockView(block, textColor) else null
                    is MarkdownBlock.HorizontalRule -> if (!isSnippetPreview) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    ) else null
                    is MarkdownBlock.ImageBlock -> if (!isSnippetPreview) ImageBlockView(block.alt, block.url) else null
                    is MarkdownBlock.Details -> if (!isSnippetPreview) DetailsBlockView(block, textColor, onChecklistToggle) else null
                }
            }
        }
    }
}

@Composable
private fun HeadingBlockView(
    heading: MarkdownBlock.Heading,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    isSnippetPreview: Boolean = false
) {
    if (isSnippetPreview) {
        Text(
            text = heading.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                color = textColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = textColor)
        3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = textColor)
        4 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, color = textColor)
        5 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = textColor)
        else -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = textColor)
    }
    Text(
        text = heading.text,
        style = style,
        modifier = Modifier.padding(top = (8 - heading.level).coerceAtLeast(2).dp)
    )
}

@Composable
private fun ParagraphBlockView(
    text: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    isSnippetPreview: Boolean = false
) {
    val context = LocalContext.current
    val annotatedString = rememberInlineMarkdown(text, isSnippetPreview)

    if (isSnippetPreview) {
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = textColor,
                lineHeight = 20.sp
            ),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            lineHeight = 24.sp
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { annotation ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
}

@Composable
private fun BlockquoteBlockView(block: MarkdownBlock.Blockquote) {
    val (containerColor, accentColor, icon) = when (block.alertType) {
        AlertType.NOTE -> Triple(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.primary, Icons.Default.Info)
        AlertType.TIP -> Triple(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.tertiary, Icons.Default.Lightbulb)
        AlertType.IMPORTANT, AlertType.WARNING, AlertType.CAUTION ->
            Triple(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.error, Icons.Default.Warning)
        null -> Triple(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), null)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (block.alertType != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = block.alertType.title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        )
                    }
                }
                block.lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (block.language.isNotBlank()) block.language.uppercase() else "CODE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", block.code))
                        copied = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp).testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun BulletListView(items: List<String>, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp, end = 10.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                ParagraphBlockView(item, textColor)
            }
        }
    }
}

@Composable
private fun NumberedListView(items: List<NumberedItem>, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Text(
                    text = "${item.number}.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.width(28.dp)
                )
                ParagraphBlockView(item.text, textColor)
            }
        }
    }
}

@Composable
private fun TaskListView(
    items: List<TaskItem>,
    onToggle: ((TaskItem) -> Unit)?,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggle?.invoke(item) }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = null, // Handled exclusively by Row clickable to avoid double toggling
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.size(36.dp).testTag("task_checkbox_${item.text.hashCode()}")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.text.ifBlank { "Task item" },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (item.checked) textColor.copy(alpha = 0.5f) else textColor,
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                    )
                )
            }
        }
    }
}

@Composable
private fun TableBlockView(table: MarkdownBlock.Table, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Headers
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .padding(8.dp)
            ) {
                table.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier
                            .width(110.dp)
                            .padding(horizontal = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Rows
            table.rows.forEachIndexed { index, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 0) Color.Transparent
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        )
                        .padding(8.dp)
                ) {
                    row.forEach { cell ->
                        Text(
                            text = cell,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .width(110.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlockView(alt: String, url: String) {
    var showPreviewDialog by remember { mutableStateOf(false) }

    val cleanUrl = url.trim()
    val isValidUrl = cleanUrl.isNotBlank() && (
        cleanUrl.startsWith("http://", ignoreCase = true) ||
        cleanUrl.startsWith("https://", ignoreCase = true) ||
        cleanUrl.startsWith("content://", ignoreCase = true) ||
        cleanUrl.startsWith("file://", ignoreCase = true)
    )

    if (!isValidUrl) {
        if (alt.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    text = alt,
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        return
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPreviewDialog = true }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SubcomposeAsyncImage(
                model = cleanUrl,
                contentDescription = alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 280.dp),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (alt.isNotBlank()) alt else "Image unavailable",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer)
                        )
                    }
                }
            )
            if (alt.isNotBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(topStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = alt,
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (showPreviewDialog) {
        Dialog(onDismissRequest = { showPreviewDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = alt,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    )
                    if (alt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = alt, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun rememberInlineMarkdown(text: String, isSnippetPreview: Boolean = false): AnnotatedString {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant
    val highlightBgColor = Color(0xFFFFF176) // Yellow highlight
    val highlightTextColor = Color(0xFF1E1F24)

    return remember(text, primaryColor, codeBgColor, isSnippetPreview) {
        buildAnnotatedString {
            // Regex token matcher for bold (**text**), italic (*text*), strikethrough (~~text~~),
            // highlight (==text==), inline code (`code`), link ([text](url))
            val pattern = Regex(
                "(\\*\\*|__)(.{1,300}?)\\1|" +          // Bold
                "(\\*|_)(.{1,300}?)\\3|" +              // Italic
                "(~~)(.{1,300}?)\\5|" +                 // Strikethrough
                "(==)(.{1,300}?)\\7|" +                 // Highlight
                "(`)(.{1,300}?)\\9|" +                  // Inline code
                "\\[(.{1,300}?)\\]\\((https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]*)\\)" // Link
            )

            var lastIndex = 0
            for (match in pattern.findAll(text)) {
                val range = match.range
                if (range.first > lastIndex) {
                    append(text.substring(lastIndex, range.first))
                }

                val fullMatch = match.value
                when {
                    // Link: [text](url)
                    fullMatch.startsWith("[") && fullMatch.contains("](") -> {
                        val linkText = match.groupValues[11]
                        val linkUrl = match.groupValues[12]
                        if (!isSnippetPreview) {
                            pushStringAnnotation(tag = "URL", annotation = linkUrl)
                            withStyle(
                                SpanStyle(
                                    color = primaryColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(linkText)
                            }
                            pop()
                        } else {
                            append(linkText)
                        }
                    }
                    // Bold: **text** or __text__
                    fullMatch.startsWith("**") || fullMatch.startsWith("__") -> {
                        val inner = fullMatch.substring(2, fullMatch.length - 2)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(inner)
                        }
                    }
                    // Strikethrough: ~~text~~
                    fullMatch.startsWith("~~") -> {
                        val inner = fullMatch.substring(2, fullMatch.length - 2)
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(inner)
                        }
                    }
                    // Highlight: ==text==
                    fullMatch.startsWith("==") -> {
                        val inner = fullMatch.substring(2, fullMatch.length - 2)
                        withStyle(SpanStyle(background = highlightBgColor, color = highlightTextColor, fontWeight = FontWeight.Medium)) {
                            append(inner)
                        }
                    }
                    // Inline code: `code`
                    fullMatch.startsWith("`") -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBgColor,
                                fontSize = 13.sp
                            )
                        ) {
                            append(" $inner ")
                        }
                    }
                    // Italic: *text* or _text_
                    fullMatch.startsWith("*") || fullMatch.startsWith("_") -> {
                        val inner = fullMatch.substring(1, fullMatch.length - 1)
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inner)
                        }
                    }
                    else -> append(fullMatch)
                }
                lastIndex = range.last + 1
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
}

@Composable
private fun DetailsBlockView(block: MarkdownBlock.Details, textColor: Color, onChecklistToggle: ((TaskItem) -> Unit)?) {
    var isExpanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = block.summary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    MarkdownRenderer(
                        markdown = block.content,
                        textColor = textColor,
                        onChecklistToggle = onChecklistToggle
                    )
                }
            }
        }
    }
}
