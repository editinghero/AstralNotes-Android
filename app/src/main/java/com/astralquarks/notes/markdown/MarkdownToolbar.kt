package com.astralquarks.notes.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownToolbar(
    onInsertText: (prefix: String, suffix: String, defaultPlaceholder: String) -> Unit,
    onOpenImageDialog: () -> Unit,
    onOpenLinkDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(
                icon = Icons.Default.Title,
                tag = "h1_btn",
                label = "H1",
                onClick = { onInsertText("# ", "", "Heading 1") }
            )
            ToolbarButton(
                icon = Icons.Default.Title,
                tag = "h2_btn",
                label = "H2",
                onClick = { onInsertText("## ", "", "Heading 2") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatBold,
                tag = "bold_btn",
                onClick = { onInsertText("**", "**", "bold text") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatItalic,
                tag = "italic_btn",
                onClick = { onInsertText("*", "*", "italic text") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatStrikethrough,
                tag = "strikethrough_btn",
                onClick = { onInsertText("~~", "~~", "strikethrough") }
            )
            ToolbarButton(
                icon = Icons.Default.BorderColor,
                tag = "highlight_btn",
                onClick = { onInsertText("==", "==", "highlighted text") }
            )
            ToolbarButton(
                icon = Icons.Default.CheckBox,
                tag = "checklist_btn",
                onClick = { onInsertText("- [ ] ", "", "Task item") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatListBulleted,
                tag = "bullet_list_btn",
                onClick = { onInsertText("- ", "", "List item") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatListNumbered,
                tag = "number_list_btn",
                onClick = { onInsertText("1. ", "", "List item") }
            )
            ToolbarButton(
                icon = Icons.Default.FormatQuote,
                tag = "quote_btn",
                onClick = { onInsertText("> ", "", "Quote text") }
            )
            ToolbarButton(
                icon = Icons.Default.Code,
                tag = "code_btn",
                onClick = { onInsertText("`", "`", "code") }
            )
            ToolbarButton(
                icon = Icons.Default.DataObject,
                tag = "code_block_btn",
                onClick = { onInsertText("```kotlin\n", "\n```", "// code here") }
            )
            ToolbarButton(
                icon = Icons.Default.TableChart,
                tag = "table_btn",
                onClick = {
                    onInsertText(
                        "| Header 1 | Header 2 |\n|---|---|\n| Cell 1 | Cell 2 |\n",
                        "",
                        ""
                    )
                }
            )
            ToolbarButton(
                icon = Icons.Default.HorizontalRule,
                tag = "hr_btn",
                onClick = { onInsertText("\n---\n", "", "") }
            )
            ToolbarButton(
                icon = Icons.Default.ArrowDropDown,
                tag = "details_btn",
                onClick = { onInsertText("<details>\n<summary>Details</summary>\n\n", "\n\n</details>", "Hidden content here") }
            )
            ToolbarButton(
                icon = Icons.Default.Link,
                tag = "link_btn",
                onClick = onOpenLinkDialog
            )
            ToolbarButton(
                icon = Icons.Default.Image,
                tag = "image_url_btn",
                onClick = onOpenImageDialog
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    tag: String,
    label: String? = null,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .testTag(tag),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = tag,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
