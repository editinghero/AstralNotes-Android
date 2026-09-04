package com.astralquarks.notes.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.util.LibraryBackupManager
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.astralquarks.notes.ai.GeminiManager
import com.astralquarks.notes.auth.AuthManager
import com.astralquarks.notes.security.VaultSecurityManager
import com.astralquarks.notes.ui.components.VaultAuthDialog
import com.astralquarks.notes.ui.theme.AppColorPalette
import com.astralquarks.notes.ui.theme.AppThemeMode
import com.astralquarks.notes.ui.theme.GeminiSparklePink
import com.astralquarks.notes.ui.theme.ThemeSettingsManager
import com.astralquarks.notes.ui.theme.TonalStyle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vaultSecurityManager: VaultSecurityManager,
    geminiManager: GeminiManager,
    authManager: AuthManager,
    onOpenDrawer: () -> Unit,
    onManualSync: () -> Unit,
    onGetAllNotesForBackup: suspend () -> List<Note>,
    onImportNotesBatch: (List<Note>, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager = remember { ThemeSettingsManager.getInstance(context) }
    val themeSettings by themeManager.themeSettings.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    val isSyncing by authManager.isSyncing.collectAsState()

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var vaultAuthenticatedForChange by remember { mutableStateOf(false) }
    var verifiedOldPassword by remember { mutableStateOf("") }

    var customApiKey by remember { mutableStateOf(geminiManager.customApiKey) }
    var customModelName by remember { mutableStateOf(geminiManager.customModelName) }
    var isThinkingMode by remember { mutableStateOf(geminiManager.isThinkingModeEnabled) }
    var isSearchGrounding by remember { mutableStateOf(geminiManager.isSearchGroundingEnabled) }

    var isSigningIn by remember { mutableStateOf(false) }

    var showExportVaultPasswordDialog by remember { mutableStateOf(false) }
    var exportVaultPassword by remember { mutableStateOf("") }
    var pendingExportNotes by remember { mutableStateOf<List<Note>?>(null) }
    var showImportVaultPasswordDialog by remember { mutableStateOf(false) }
    var importVaultPassword by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isProcessingBackup by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExportNotes != null) {
            isProcessingBackup = true
            scope.launch {
                val res = LibraryBackupManager.exportLibrary(
                    context = context,
                    allNotes = pendingExportNotes!!,
                    vaultKey = vaultSecurityManager.getVaultKey(),
                    exportVaultPassword = exportVaultPassword.ifBlank { null },
                    destinationUri = uri
                )
                isProcessingBackup = false
                pendingExportNotes = null
                exportVaultPassword = ""
                res.onSuccess { count ->
                    Toast.makeText(context, "Exported $count notes to backup", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessingBackup = true
            scope.launch {
                val inspection = LibraryBackupManager.inspectBackup(context, uri)
                isProcessingBackup = false
                inspection.onSuccess { (_, vaultCount) ->
                    if (vaultCount > 0) {
                        pendingImportUri = uri
                        showImportVaultPasswordDialog = true
                    } else {
                        isProcessingBackup = true
                        val importRes = LibraryBackupManager.importLibrary(context, uri, null, null)
                        isProcessingBackup = false
                        importRes.onSuccess { result ->
                            onImportNotesBatch(result.importedNotes) {
                                Toast.makeText(context, "Imported ${result.regularNotesImported} notes", Toast.LENGTH_LONG).show()
                            }
                        }.onFailure { err ->
                            Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }.onFailure { err ->
                    Toast.makeText(context, "Cannot read backup: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Preferences",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("settings_menu_button")) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // TOP SECTION: Firebase Auth & Cloud Firestore Sync
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (authManager.isSignedIn)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (authManager.isSignedIn)
                    androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                else
                    null
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (authManager.isSignedIn && !authManager.userPhotoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = authManager.userPhotoUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (authManager.isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (authManager.isSignedIn)
                                    (authManager.userDisplayName ?: "Signed In")
                                else
                                    "Google Account & Cloud Sync",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (authManager.isSignedIn)
                                    (authManager.userEmail ?: "Synchronized with Firestore")
                                else
                                    "Offline-First mode • Sign in to backup notes",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }

                    if (authManager.isSignedIn) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onManualSync,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        authManager.signOut()
                                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Sign Out")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSigningIn = true
                                    val res = authManager.signInWithGoogle(context)
                                    isSigningIn = false
                                    res.onSuccess {
                                        Toast.makeText(context, "Signed in as ${it.displayName ?: it.email}", Toast.LENGTH_SHORT).show()
                                    }
                                    res.onFailure {
                                        Toast.makeText(context, "${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("google_signin_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Google")
                            }
                        }
                    }
                }
            }

            // SECTION 1: Material You & Expressive Theme Customization
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Material Theme & Dynamic Colors",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "AstralNotes and note card defaults follow your selected Material You expressive palette.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Theme Mode
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AppThemeMode.SYSTEM to "System",
                            AppThemeMode.LIGHT to "Light",
                            AppThemeMode.DARK to "Dark"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeSettings.themeMode == mode,
                                onClick = { themeManager.setThemeMode(mode) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Material Color Palette
                    Text(
                        text = "Material Color Palette",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppColorPalette.values().forEach { palette ->
                            val isSelected = themeSettings.colorPalette == palette
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { themeManager.setColorPalette(palette) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(palette.primaryColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = palette.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isSelected) {
                                        Text(
                                            text = "Active",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Official Android Tonal Styles
                    Text(
                        text = "Official Android Dynamic Tonal Styles",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TonalStyle.values().forEach { style ->
                            FilterChip(
                                selected = themeSettings.tonalStyle == style && themeSettings.colorPalette == AppColorPalette.DYNAMIC_MATERIAL_YOU,
                                onClick = {
                                    themeManager.setColorPalette(AppColorPalette.DYNAMIC_MATERIAL_YOU)
                                    themeManager.setTonalStyle(style)
                                },
                                label = { Text(style.displayName) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // SECTION 2: Vault Security & Authentication
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Private Vault Security",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Your private vault encrypts hidden notes using a dedicated master password separate from your Google account.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = { showPasswordChangeDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("change_vault_password_button")
                    ) {
                        Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (!vaultSecurityManager.isPasswordSet()) "Set Custom Vault Password" else "Change Custom Vault Password")
                    }
                }
            }

            // SECTION 3: Google Gemini AI
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GeminiSparklePink
                        )
                        Text(
                            text = "Google Gemini Intelligence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it },
                        label = { Text("Custom Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_custom_api_key")
                    )

                    OutlinedTextField(
                        value = customModelName,
                        onValueChange = { customModelName = it },
                        label = { Text("Custom Model Name") },
                        placeholder = { Text("gemini-3.7-flash") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_custom_model")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("High Thinking Mode", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Uses gemini-3.1-pro-preview with deep multi-step reasoning.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Switch(
                            checked = isThinkingMode,
                            onCheckedChange = { isThinkingMode = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Search Grounding", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Ground AI responses with Google Search.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Switch(
                            checked = isSearchGrounding,
                            onCheckedChange = { isSearchGrounding = it }
                        )
                    }

                    Button(
                        onClick = {
                            geminiManager.customApiKey = customApiKey.trim()
                            geminiManager.customModelName = customModelName.trim()
                            geminiManager.isThinkingModeEnabled = isThinkingMode
                            geminiManager.isSearchGroundingEnabled = isSearchGrounding
                            Toast.makeText(context, "Gemini settings updated successfully", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_save_gemini")
                    ) {
                        Text("Save Gemini Config")
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Library Backup & Restore",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Export your entire notes database to a JSON backup file, or restore a backup created on Android or Web. Vault notes are protected with your vault password.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isProcessingBackup) return@Button
                                scope.launch {
                                    val notes = onGetAllNotesForBackup()
                                    pendingExportNotes = notes
                                    val hasVault = notes.any { it.isLocked }
                                    if (hasVault) {
                                        showExportVaultPasswordDialog = true
                                    } else {
                                        createDocumentLauncher.launch("astral_backup_${System.currentTimeMillis()}.json")
                                    }
                                }
                            },
                            enabled = !isProcessingBackup,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }

                        OutlinedButton(
                            onClick = {
                                if (isProcessingBackup) return@OutlinedButton
                                openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            enabled = !isProcessingBackup,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import")
                        }
                    }

                    if (isProcessingBackup) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Processing backup...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showExportVaultPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showExportVaultPasswordDialog = false
                pendingExportNotes = null
                exportVaultPassword = ""
            },
            title = { Text("Vault Password Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your library contains locked vault notes. Enter your current vault password to securely encrypt them in the backup file.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    OutlinedTextField(
                        value = exportVaultPassword,
                        onValueChange = { exportVaultPassword = it },
                        label = { Text("Vault Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (exportVaultPassword.isBlank()) {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            val unlockRes = vaultSecurityManager.unlockWithPassword(exportVaultPassword)
                            if (unlockRes.isSuccess) {
                                showExportVaultPasswordDialog = false
                                createDocumentLauncher.launch("astral_backup_${System.currentTimeMillis()}.json")
                            } else {
                                Toast.makeText(context, "Incorrect vault password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Proceed to Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExportVaultPasswordDialog = false
                        pendingExportNotes = null
                        exportVaultPassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showImportVaultPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportVaultPasswordDialog = false
                pendingImportUri = null
                importVaultPassword = ""
            },
            title = { Text("Imported Vault Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This backup file contains private vault notes. Enter the vault password used when this backup file was exported.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    OutlinedTextField(
                        value = importVaultPassword,
                        onValueChange = { importVaultPassword = it },
                        label = { Text("Imported File Vault Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importVaultPassword.isBlank()) {
                            Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val uri = pendingImportUri
                        val pass = importVaultPassword
                        showImportVaultPasswordDialog = false
                        pendingImportUri = null
                        importVaultPassword = ""

                        if (uri != null) {
                            isProcessingBackup = true
                            scope.launch {
                                val importRes = LibraryBackupManager.importLibrary(
                                    context = context,
                                    uri = uri,
                                    importedVaultPassword = pass,
                                    currentVaultKey = vaultSecurityManager.getVaultKey()
                                )
                                isProcessingBackup = false
                                importRes.onSuccess { result ->
                                    onImportNotesBatch(result.importedNotes) {
                                        Toast.makeText(
                                            context,
                                            "Imported ${result.regularNotesImported} regular notes and ${result.vaultNotesImported} vault notes",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }.onFailure { err ->
                                    Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Unlock & Import")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportVaultPasswordDialog = false
                        pendingImportUri = null
                        importVaultPassword = ""
                    }
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showPasswordChangeDialog) {
        if (vaultSecurityManager.isPasswordSet() && !vaultAuthenticatedForChange) {
            var oldPassAttempt by remember { mutableStateOf("") }
            VaultAuthDialog(
                isPasswordSet = true,
                onVerifyPassword = { pass ->
                    val res = vaultSecurityManager.verifyPassword(pass)
                    if (res.isSuccess) {
                        oldPassAttempt = pass
                    }
                    res
                },
                onSetPassword = { Result.success(Unit) },
                onDismiss = {
                    showPasswordChangeDialog = false
                    vaultAuthenticatedForChange = false
                    verifiedOldPassword = ""
                },
                onSuccess = {
                    verifiedOldPassword = oldPassAttempt
                    vaultAuthenticatedForChange = true
                }
            )
        } else {
            VaultAuthDialog(
                isPasswordSet = false,
                onVerifyPassword = { Result.success(true) },
                onSetPassword = { newPass ->
                    val res = if (vaultSecurityManager.isPasswordSet() && verifiedOldPassword.isNotBlank()) {
                        vaultSecurityManager.changePassword(verifiedOldPassword, newPass)
                    } else {
                        vaultSecurityManager.setupNewVault(newPass)
                    }
                    if (res.isSuccess) {
                        showPasswordChangeDialog = false
                        vaultAuthenticatedForChange = false
                        verifiedOldPassword = ""
                        Toast.makeText(context, "Vault password saved successfully", Toast.LENGTH_SHORT).show()
                    }
                    res
                },
                onDismiss = {
                    showPasswordChangeDialog = false
                    vaultAuthenticatedForChange = false
                    verifiedOldPassword = ""
                },
                onSuccess = { }
            )
        }
    }
}
