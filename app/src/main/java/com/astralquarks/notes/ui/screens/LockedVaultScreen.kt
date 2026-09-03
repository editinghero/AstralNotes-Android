package com.astralquarks.notes.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.security.VaultAuthMode
import com.astralquarks.notes.security.VaultSecurityManager
import com.astralquarks.notes.ui.components.NoteCard
import com.astralquarks.notes.ui.components.VaultAuthDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedVaultScreen(
    lockedNotes: List<Note>,
    isVaultUnlocked: Boolean,
    vaultSecurityManager: VaultSecurityManager,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onCreateLockedNote: () -> Unit,
    onUnlockToPublic: (Note) -> Unit,
    onPermanentlyDelete: (Note) -> Unit,
    onRelockVault: () -> Unit,
    onTriggerBiometric: (onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAuthDialog by remember { mutableStateOf(!isVaultUnlocked) }
    val hasRemoteVault by vaultSecurityManager.hasVault.collectAsState()

    LaunchedEffect(Unit) {
        vaultSecurityManager.checkVaultExists()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("locked_vault_screen"),
        topBar = {
            TopAppBar(
                title = {
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
                            text = "Private Vault",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("vault_menu_button")) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (isVaultUnlocked) {
                        FilledTonalButton(
                            onClick = onRelockVault,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .testTag("relock_vault_button")
                                .padding(end = 14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Relock Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isVaultUnlocked) {
                FloatingActionButton(
                    onClick = onCreateLockedNote,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.testTag("create_locked_note_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Locked Note")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isVaultUnlocked) {
                // Locked Gate Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        Text(
                            text = "Private Vault is Locked",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "Your private notes are encrypted and isolated from the main dashboard.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showAuthDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(48.dp)
                                .testTag("unlock_vault_prompt_button")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (!vaultSecurityManager.isPasswordSet()) "Set Up Vault Password" else "Unlock Vault")
                        }

                        if (vaultSecurityManager.isPasswordSet() &&
                            vaultSecurityManager.isBiometricAvailable() &&
                            vaultSecurityManager.getAuthMode() != VaultAuthMode.PASSWORD_ONLY
                        ) {
                            FilledTonalButton(
                                onClick = { onTriggerBiometric { vaultSecurityManager.unlockVault() } },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(48.dp)
                                    .testTag("vault_biometric_quick_button")
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Biometric Unlock")
                            }
                        }
                    }
                }
            } else {
                // Vault Unlocked Notes Content
                if (lockedNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Vault is empty",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Lock any note from the menu to keep it hidden here.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                            Button(
                                onClick = onCreateLockedNote,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Locked Note")
                            }
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp
                    ) {
                        items(lockedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note) },
                                onToggleLock = { onUnlockToPublic(note) },
                                onMoveToTrash = { onPermanentlyDelete(note) },
                                isLockedSection = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAuthDialog) {
        val isExistingVault = (hasRemoteVault == true) || vaultSecurityManager.isPasswordSet()
        VaultAuthDialog(
            isPasswordSet = isExistingVault,
            authMode = vaultSecurityManager.getAuthMode(),
            isBiometricAvailable = vaultSecurityManager.isBiometricAvailable(),
            onVerifyPassword = { password -> vaultSecurityManager.unlockWithPassword(password) },
            onSetPassword = { password -> vaultSecurityManager.setupNewVault(password) },
            onTriggerBiometric = onTriggerBiometric,
            onDismiss = { showAuthDialog = false },
            onSuccess = {
                showAuthDialog = false
            }
        )
    }
}
