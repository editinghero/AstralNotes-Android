package com.astralquarks.notes.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.astralquarks.notes.util.CatboxUploader
import kotlinx.coroutines.launch

@Composable
fun ImageUrlDialog(
    onDismiss: () -> Unit,
    onInsertImage: (url: String, alt: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Upload to Catbox, 1: Enter URL
    var url by remember { mutableStateOf("") }
    var alt by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isUploading = true
                error = null
                uploadStatus = "Uploading to Catbox (Permanent)..."
                val result = CatboxUploader.uploadFile(context, uri)
                isUploading = false
                result.onSuccess { uploadedUrl ->
                    url = uploadedUrl
                    uploadStatus = "Uploaded to Catbox successfully!"
                }
                result.onFailure { err ->
                    error = err.localizedMessage ?: "Upload failed"
                    uploadStatus = null
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        shape = RoundedCornerShape(32.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Insert Image",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Two clear segmented modes
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Upload (Catbox)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Image URL", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                if (selectedTabIndex == 0) {
                    // Option 1: Catbox Permanent Upload
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Permanent, unlimited image hosting powered by Catbox.moe.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        FilledTonalButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            enabled = !isUploading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("catbox_upload_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uploading to Catbox...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (url.isNotBlank()) "Choose Another Image" else "Choose Image from Device")
                                }
                            }
                        }

                        if (uploadStatus != null && error == null) {
                            Text(
                                text = uploadStatus!!,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else {
                    // Option 2: Enter by URL directly
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Link any direct web image URL (HTTPS).",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                error = null
                            },
                            label = { Text("Image URL (https://...)") },
                            placeholder = { Text("https://files.catbox.moe/... or image url") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth().testTag("image_url_input")
                        )
                    }
                }

                // Alt / Caption input
                OutlinedTextField(
                    value = alt,
                    onValueChange = { alt = it },
                    label = { Text("Caption / Alt Description (optional)") },
                    placeholder = { Text("Image description") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("image_alt_input")
                )

                // Live Preview
                if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Live Image Preview:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = url.trim(),
                            contentDescription = alt,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (error != null) {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
                        error = "Please upload an image or enter a valid HTTP/HTTPS URL."
                    } else {
                        onInsertImage(cleanUrl, alt.trim())
                    }
                },
                enabled = !isUploading && url.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("insert_image_confirm_button")
            ) {
                Text("Insert Image")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
