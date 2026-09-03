package com.astralquarks.notes.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.util.NoteExporter
import com.astralquarks.notes.util.WebShareManager
import kotlinx.coroutines.launch

@Composable
fun ShareNoteDialog(
    note: Note,
    onSelectFormat: (NoteExporter.ExportFormat) -> Unit,
    isExporting: Boolean = false,
    exportStatus: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isConfiguringWebShare by remember { mutableStateOf(false) }
    var webSharePassword by remember { mutableStateOf("") }
    var webShareExpiryHours by remember { mutableStateOf<Int?>(null) }
    var isGeneratingWebShare by remember { mutableStateOf(false) }
    var generatedWebShareUrl by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isExporting && !isGeneratingWebShare) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = when {
                        isExporting -> "Exporting Document"
                        isConfiguringWebShare -> "Web Share Link"
                        else -> "Share Note"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isExporting) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = exportStatus ?: "Preparing export...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                } else if (isConfiguringWebShare) {
                    if (generatedWebShareUrl != null) {
                        Text(
                            text = "Web share link generated successfully:",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = generatedWebShareUrl!!,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", generatedWebShareUrl))
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Copy")
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, generatedWebShareUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Share")
                            }
                        }
                    } else {
                        Text(
                            text = "Create a cloud web link for \"${note.title.ifBlank { "Untitled" }}\". Anyone with the link can view it in their browser.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        OutlinedTextField(
                            value = webSharePassword,
                            onValueChange = { webSharePassword = it },
                            label = { Text("Password Protection (Optional)") },
                            placeholder = { Text("Leave empty for public link") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text(
                            text = "Expiration Duration:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = webShareExpiryHours == null,
                                onClick = { webShareExpiryHours = null },
                                label = { Text("Never") }
                            )
                            FilterChip(
                                selected = webShareExpiryHours == 1,
                                onClick = { webShareExpiryHours = 1 },
                                label = { Text("1h") }
                            )
                            FilterChip(
                                selected = webShareExpiryHours == 24,
                                onClick = { webShareExpiryHours = 24 },
                                label = { Text("1d") }
                            )
                            FilterChip(
                                selected = webShareExpiryHours == 168,
                                onClick = { webShareExpiryHours = 168 },
                                label = { Text("7d") }
                            )
                        }

                        Button(
                            onClick = {
                                isGeneratingWebShare = true
                                scope.launch {
                                    val res = WebShareManager.createWebShare(
                                        note = note,
                                        password = webSharePassword,
                                        expiryHours = webShareExpiryHours
                                    )
                                    isGeneratingWebShare = false
                                    res.onSuccess { url ->
                                        generatedWebShareUrl = url
                                    }.onFailure { err ->
                                        Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isGeneratingWebShare,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isGeneratingWebShare) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.size(8.dp))
                                Text("Generate Web Link")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Choose format to share or export \"${note.title.ifBlank { "Untitled" }}\":",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ShareOptionItem(
                        title = "Web Share Link",
                        description = "Create public or password-protected web reader link",
                        icon = Icons.Default.Link,
                        onClick = { isConfiguringWebShare = true },
                        testTag = "share_web_option"
                    )

                    ShareOptionItem(
                        title = "Markdown (.md)",
                        description = "Raw markdown with tags, headings, and checklists",
                        icon = Icons.Default.Code,
                        onClick = { onSelectFormat(NoteExporter.ExportFormat.MARKDOWN) },
                        testTag = "share_md_option"
                    )

                    ShareOptionItem(
                        title = "HTML Web Page (.html)",
                        description = "Formatted styled web document ready to view or print",
                        icon = Icons.Default.Description,
                        onClick = { onSelectFormat(NoteExporter.ExportFormat.HTML) },
                        testTag = "share_html_option"
                    )

                    ShareOptionItem(
                        title = "PDF Document (.pdf)",
                        description = "Printable formatted document compatible with all readers",
                        icon = Icons.Default.PictureAsPdf,
                        onClick = { onSelectFormat(NoteExporter.ExportFormat.PDF) },
                        testTag = "share_pdf_option"
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isExporting && !isGeneratingWebShare) {
                TextButton(
                    onClick = {
                        if (isConfiguringWebShare && generatedWebShareUrl == null) {
                            isConfiguringWebShare = false
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Text(if (isConfiguringWebShare && generatedWebShareUrl == null) "Back" else "Close")
                }
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}

@Composable
private fun ShareOptionItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
