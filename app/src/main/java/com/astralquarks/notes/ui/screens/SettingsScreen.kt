package com.astralquarks.notes.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.astralquarks.notes.ai.GeminiManager
import com.astralquarks.notes.auth.AuthManager
import com.astralquarks.notes.security.VaultAuthMode
import com.astralquarks.notes.security.VaultSecurityManager
import com.astralquarks.notes.ui.components.VaultAuthDialog
import com.astralquarks.notes.ui.theme.GeminiSparklePink
import kotlinx.coroutines.launch

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.collectAsState
import com.astralquarks.notes.ui.theme.AppColorPalette
import com.astralquarks.notes.ui.theme.AppThemeMode
import com.astralquarks.notes.ui.theme.ThemeSettingsManager
import com.astralquarks.notes.ui.theme.TonalStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vaultSecurityManager: VaultSecurityManager,
    geminiManager: GeminiManager,
    authManager: AuthManager,
    onOpenDrawer: () -> Unit,
    onManualSync: () -> Unit,
    onTriggerBiometric: (onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager = remember { ThemeSettingsManager.getInstance(context) }
    val themeSettings by themeManager.themeSettings.collectAsState()

    var showPasswordChangeDialog by remember { mutableStateOf(false) }
    var vaultAuthenticatedForChange by remember { mutableStateOf(false) }
    var currentAuthMode by remember { mutableStateOf(vaultSecurityManager.getAuthMode()) }

    var customApiKey by remember { mutableStateOf(geminiManager.customApiKey) }
    var customModelName by remember { mutableStateOf(geminiManager.customModelName) }
    var isThinkingMode by remember { mutableStateOf(geminiManager.isThinkingModeEnabled) }
    var isSearchGrounding by remember { mutableStateOf(geminiManager.isSearchGroundingEnabled) }

    var isSigningIn by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings & Appearance",
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
                        text = "AstralNotes and all note defaults follow your selected Material You expressive dynamic palette.",
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
                        text = "Customize unlock methods for hidden locked notes. Android device screen lock is strictly disabled in favor of dedicated custom security.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Mode 1: Custom Password Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentAuthMode == VaultAuthMode.PASSWORD_ONLY,
                            onClick = {
                                currentAuthMode = VaultAuthMode.PASSWORD_ONLY
                                vaultSecurityManager.setAuthMode(VaultAuthMode.PASSWORD_ONLY)
                            },
                            modifier = Modifier.testTag("mode_password_only")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Custom Password Only", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Unlock using your custom alphanumeric PIN or password.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }

                    // Mode 2: Biometric Only
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentAuthMode == VaultAuthMode.BIOMETRIC_ONLY,
                            onClick = {
                                if (vaultSecurityManager.isBiometricAvailable()) {
                                    currentAuthMode = VaultAuthMode.BIOMETRIC_ONLY
                                    vaultSecurityManager.setAuthMode(VaultAuthMode.BIOMETRIC_ONLY)
                                } else {
                                    Toast.makeText(context, "Biometrics not available on this device", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.testTag("mode_biometric_only")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Biometric Only (Fingerprint)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Quick biometric sensor authentication without password prompt.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }

                    // Mode 3: Custom Password with Biometric
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentAuthMode == VaultAuthMode.PASSWORD_AND_BIOMETRIC,
                            onClick = {
                                currentAuthMode = VaultAuthMode.PASSWORD_AND_BIOMETRIC
                                vaultSecurityManager.setAuthMode(VaultAuthMode.PASSWORD_AND_BIOMETRIC)
                            },
                            modifier = Modifier.testTag("mode_password_and_biometric")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Custom Password + Biometric", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text("Authenticate with either biometric fingerprint or custom password.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }

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

            // SECTION 2: Google Gemini AI
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

            // SECTION 3: Firebase Auth & Cloud Firestore Sync
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
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Cloud Sync & Firebase Auth",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = if (authManager.isSignedIn)
                            "Signed in as ${authManager.userDisplayName ?: authManager.userEmail}. All notes are encrypted and synchronized with Cloud Firestore."
                        else
                            "Currently in Offline-First mode. Notes persist locally on device with Room DB. Sign in to synchronize notes across devices.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    var customWebClientId by remember { mutableStateOf(authManager.customWebClientId) }

                    OutlinedTextField(
                        value = customWebClientId,
                        onValueChange = {
                            customWebClientId = it
                            authManager.customWebClientId = it.trim()
                        },
                        label = { Text("Firebase Web Client ID") },
                        placeholder = { Text("173977964592-xxxx.apps.googleusercontent.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("settings_web_client_id")
                    )

                    if (authManager.isSignedIn) {
                        FilledTonalButton(
                            onClick = onManualSync,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trigger Cloud Sync Now")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    authManager.signOut()
                                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    isSigningIn = true
                                    val res = authManager.signInWithGoogle()
                                    isSigningIn = false
                                    res.onSuccess {
                                        Toast.makeText(context, "Signed in as ${it.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    res.onFailure {
                                        Toast.makeText(context, "${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("google_signin_button")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Google")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showPasswordChangeDialog) {
        if (vaultSecurityManager.isPasswordSet() && !vaultAuthenticatedForChange) {
            VaultAuthDialog(
                isPasswordSet = true,
                authMode = currentAuthMode,
                isBiometricAvailable = vaultSecurityManager.isBiometricAvailable(),
                onVerifyPassword = { vaultSecurityManager.verifyPassword(it) },
                onSetPassword = { },
                onTriggerBiometric = onTriggerBiometric,
                onDismiss = { showPasswordChangeDialog = false },
                onSuccess = {
                    vaultAuthenticatedForChange = true
                }
            )
        } else {
            VaultAuthDialog(
                isPasswordSet = false, // force new setup flow
                authMode = currentAuthMode,
                isBiometricAvailable = vaultSecurityManager.isBiometricAvailable(),
                onVerifyPassword = { true },
                onSetPassword = {
                    vaultSecurityManager.setPassword(it)
                    Toast.makeText(context, "New vault password set", Toast.LENGTH_SHORT).show()
                },
                onTriggerBiometric = onTriggerBiometric,
                onDismiss = { 
                    showPasswordChangeDialog = false 
                    vaultAuthenticatedForChange = false
                },
                onSuccess = {
                    showPasswordChangeDialog = false
                    vaultAuthenticatedForChange = false
                }
            )
        }
    }
}
