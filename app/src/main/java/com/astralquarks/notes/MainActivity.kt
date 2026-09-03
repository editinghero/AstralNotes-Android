package com.astralquarks.notes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.ui.components.AiAssistantBottomSheet
import com.astralquarks.notes.ui.components.DrawerDestination
import com.astralquarks.notes.ui.components.ExpressiveDrawerContent
import android.net.Uri
import com.astralquarks.notes.ui.screens.ArchiveScreen
import com.astralquarks.notes.ui.screens.DashboardScreen
import com.astralquarks.notes.ui.screens.HomeScreen
import com.astralquarks.notes.ui.screens.LockedVaultScreen
import com.astralquarks.notes.ui.screens.NoteEditScreen
import com.astralquarks.notes.ui.screens.SettingsScreen
import com.astralquarks.notes.ui.screens.SignInGateScreen
import com.astralquarks.notes.ui.screens.TagsScreen
import com.astralquarks.notes.ui.screens.TrashScreen
import com.astralquarks.notes.ui.theme.MyApplicationTheme
import com.astralquarks.notes.viewmodel.NotesViewModel
import com.astralquarks.notes.widget.QuickNoteWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val viewModel: NotesViewModel by viewModels()
    private val pendingAction = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingAction.value = intent
        setContent {
            MyApplicationTheme {
                MainAppContent(
                    activity = this,
                    viewModel = viewModel,
                    pendingIntentFlow = pendingAction
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAction.value = intent
    }
}

sealed class Screen {
    object Home : Screen()
    data class EditNote(val noteId: String?, val initialContent: String = "", val fromVault: Boolean = false) : Screen()
    object Tags : Screen()
    object LockedVault : Screen()
    object Dashboard : Screen()
    object Archive : Screen()
    object Trash : Screen()
    object Settings : Screen()
}

@Composable
fun MainAppContent(
    activity: FragmentActivity,
    viewModel: NotesViewModel,
    pendingIntentFlow: MutableStateFlow<Intent?>
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val activeNotes by viewModel.activeNotes.collectAsState()
    val pinnedNotes by viewModel.pinnedNotes.collectAsState()
    val lockedNotes by viewModel.lockedNotes.collectAsState()
    val archivedNotes by viewModel.archivedNotes.collectAsState()
    val trashNotes by viewModel.trashNotes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val currentDestination by viewModel.currentDestination.collectAsState()

    val currentUser by viewModel.authManager.currentUser.collectAsState()
    val isSyncing by viewModel.authManager.isSyncing.collectAsState()
    val syncStatus by viewModel.authManager.syncStatus.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var showGlobalAiSheet by remember { mutableStateOf(false) }

    // Collect pending intents from widget actions
    val pendingIntent by pendingIntentFlow.collectAsState()
    LaunchedEffect(pendingIntent) {
        val current = pendingIntent ?: return@LaunchedEffect
        when (current.action) {
            QuickNoteWidgetProvider.ACTION_NEW_NOTE -> {
                currentScreen = Screen.EditNote(null, "")
            }
            QuickNoteWidgetProvider.ACTION_NEW_CHECKLIST -> {
                currentScreen = Screen.EditNote(null, "- [ ] ")
            }
            QuickNoteWidgetProvider.ACTION_NEW_IMAGE_NOTE -> {
                currentScreen = Screen.EditNote(null, "")
            }
            QuickNoteWidgetProvider.ACTION_OPEN_AI -> {
                showGlobalAiSheet = true
            }
            Intent.ACTION_VIEW -> {
                val noteId = current.getStringExtra(QuickNoteWidgetProvider.EXTRA_NOTE_ID)
                if (!noteId.isNullOrBlank()) {
                    currentScreen = Screen.EditNote(noteId, "")
                }
            }
        }
        pendingIntentFlow.value = null
    }

    if (currentUser == null) {
        SignInGateScreen(
            onSignIn = {
                scope.launch {
                    val res = viewModel.authManager.signInWithGoogle(activity)
                    if (res.isFailure) {
                        Toast.makeText(activity, res.exceptionOrNull()?.localizedMessage ?: "Sign in failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        return
    }

    BackHandler(enabled = drawerState.isOpen || currentScreen !is Screen.Home) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentScreen !is Screen.Home) {
            currentScreen = Screen.Home
            viewModel.setDestination(DrawerDestination.NOTES)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ExpressiveDrawerContent(
                currentDestination = currentDestination,
                onSelectDestination = { dest ->
                    viewModel.setDestination(dest)
                    scope.launch { drawerState.close() }
                    when (dest) {
                        DrawerDestination.NOTES -> currentScreen = Screen.Home
                        DrawerDestination.PINNED -> currentScreen = Screen.Home
                        DrawerDestination.TAGS -> currentScreen = Screen.Tags
                        DrawerDestination.LOCKED_VAULT -> currentScreen = Screen.LockedVault
                        DrawerDestination.AI_ASSISTANT -> showGlobalAiSheet = true
                        DrawerDestination.DASHBOARD -> currentScreen = Screen.Dashboard
                        DrawerDestination.ARCHIVE -> currentScreen = Screen.Archive
                        DrawerDestination.TRASH -> currentScreen = Screen.Trash
                        DrawerDestination.SETTINGS -> currentScreen = Screen.Settings
                    }
                },
                lockedCount = lockedNotes.size,
                activeNotesCount = activeNotes.size,
                userDisplayName = currentUser?.displayName,
                isSignedIn = currentUser != null,
                onOpenWebApp = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://astralnotesweb.pages.dev"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState is Screen.EditNote) {
                    // Container Transform Enter (from note list to editor)
                    (fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 0.90f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.05f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                } else if (initialState is Screen.EditNote) {
                    // Container Transform Exit (from editor back to list)
                    (fadeIn(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            scaleIn(initialScale = 1.05f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))) togetherWith
                            (fadeOut(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 0.90f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                } else {
                    // Fluid horizontal slide & fade for other top-level screens
                    (slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            fadeIn(animationSpec = tween(220))) togetherWith
                            (slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                    fadeOut(animationSpec = tween(180)))
                }
            },
            label = "material_motion_screen_transition"
        ) { screen ->
            when (screen) {
                is Screen.Home -> {
                    HomeScreen(
                        notes = if (currentDestination == DrawerDestination.PINNED) pinnedNotes else activeNotes,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNoteClick = { note -> currentScreen = Screen.EditNote(note.id) },
                        onCreateNote = { initContent -> currentScreen = Screen.EditNote(null, initContent) },
                        onTogglePin = { viewModel.togglePin(it) },
                        onToggleArchive = { viewModel.toggleArchive(it) },
                        onMoveToTrash = { viewModel.moveToTrash(it) },
                        onToggleLock = { viewModel.toggleLock(it) },
                        onUpdateNoteColor = { note, color -> viewModel.updateNoteColor(note, color) },
                        onOpenAiSheet = { showGlobalAiSheet = true },
                        onProfileClick = {
                            viewModel.setDestination(DrawerDestination.SETTINGS)
                            currentScreen = Screen.Settings
                        },
                        userPhotoUrl = currentUser?.photoUrl?.toString(),
                        userDisplayName = currentUser?.displayName,
                        isSyncing = isSyncing,
                        syncStatus = syncStatus
                    )
                }

                is Screen.EditNote -> {
                    val allTags = remember(activeNotes) {
                        activeNotes.flatMap { it.tags }.distinct().sorted()
                    }
                    val noteChatMessages by viewModel.getChatMessages(screen.noteId).collectAsState(initial = emptyList())
                    NoteEditScreen(
                        noteId = screen.noteId,
                        initialContent = screen.initialContent,
                        allExistingTags = allTags,
                        persistedMessages = noteChatMessages,
                        onSaveChatMessage = { isUser, text -> viewModel.saveChatMessage(screen.noteId, isUser, text) },
                        onClearChatHistory = { viewModel.clearChatHistory(screen.noteId) },
                        getNote = { id -> viewModel.repository.getNoteById(id) },
                        onSaveNote = { note -> viewModel.saveNote(note) },
                        onDeleteNote = { note -> viewModel.moveToTrash(note) },
                        onBack = { currentScreen = if (screen.fromVault) Screen.LockedVault else Screen.Home },
                        geminiManager = viewModel.geminiManager
                    )
                }

                is Screen.Tags -> {
                    TagsScreen(
                        allNotes = activeNotes,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSelectTagFilter = { tag ->
                            viewModel.setSingleTagFilter(tag)
                            viewModel.setDestination(DrawerDestination.NOTES)
                            currentScreen = Screen.Home
                        },
                        onRenameTag = { oldTag, newTag ->
                            viewModel.renameTag(oldTag, newTag)
                        },
                        onDeleteTag = { tag ->
                            viewModel.deleteTag(tag)
                        }
                    )
                }

                is Screen.LockedVault -> {
                    LockedVaultScreen(
                        lockedNotes = lockedNotes,
                        isVaultUnlocked = isVaultUnlocked,
                        vaultSecurityManager = viewModel.vaultSecurityManager,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNoteClick = { note -> currentScreen = Screen.EditNote(note.id, fromVault = true) },
                        onCreateLockedNote = {
                            val newNote = Note(
                                id = java.util.UUID.randomUUID().toString(),
                                title = "Private Locked Note",
                                content = "# Confidential\n\n- [ ] Private task",
                                isLocked = true,
                                colorHex = "#FFF9C4"
                            )
                            viewModel.saveNote(newNote)
                            currentScreen = Screen.EditNote(newNote.id, fromVault = true)
                        },
                        onUnlockToPublic = { viewModel.toggleLock(it) },
                        onPermanentlyDelete = { viewModel.permanentlyDelete(it) },
                        onRelockVault = { viewModel.relockVault() },
                        onTriggerBiometric = { onSuccessCallback ->
                            viewModel.triggerBiometricPrompt(
                                activity = activity,
                                onSuccess = {
                                    onSuccessCallback()
                                },
                                onError = { msg ->
                                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }

                is Screen.Dashboard -> {
                    DashboardScreen(
                        allNotes = activeNotes,
                        archivedNotes = archivedNotes,
                        isSignedIn = currentUser != null,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNoteClick = { note -> currentScreen = Screen.EditNote(note.id) },
                        onNavigateToArchive = {
                            viewModel.setDestination(DrawerDestination.ARCHIVE)
                            currentScreen = Screen.Archive
                        }
                    )
                }

                is Screen.Archive -> {
                    ArchiveScreen(
                        archivedNotes = archivedNotes,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNoteClick = { note -> currentScreen = Screen.EditNote(note.id) },
                        onUnarchive = { viewModel.toggleArchive(it) },
                        onMoveToTrash = { viewModel.moveToTrash(it) }
                    )
                }

                is Screen.Trash -> {
                    TrashScreen(
                        trashNotes = trashNotes,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onRestoreNote = { viewModel.restoreFromTrash(it) },
                        onPermanentlyDelete = { viewModel.permanentlyDelete(it) },
                        onClearTrash = { viewModel.clearTrash() }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        vaultSecurityManager = viewModel.vaultSecurityManager,
                        geminiManager = viewModel.geminiManager,
                        authManager = viewModel.authManager,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onManualSync = { viewModel.manualSyncAll() },
                        onTriggerBiometric = { onSuccessCallback ->
                            viewModel.triggerBiometricPrompt(
                                activity = activity,
                                onSuccess = {
                                    onSuccessCallback()
                                },
                                onError = { msg ->
                                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    if (showGlobalAiSheet) {
        val globalChatMessages by viewModel.getChatMessages(null).collectAsState(initial = emptyList())
        AiAssistantBottomSheet(
            note = null,
            allNotes = activeNotes,
            persistedMessages = globalChatMessages,
            onSaveChatMessage = { isUser, text -> viewModel.saveChatMessage(null, isUser, text) },
            onClearChatHistory = { viewModel.clearChatHistory(null) },
            geminiManager = viewModel.geminiManager,
            onDismiss = { showGlobalAiSheet = false },
            onInsertTextIntoNote = null,
            onCreateNewNote = { title, content ->
                val newNote = Note(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    colorHex = "#DEFAULT",
                    updatedAt = System.currentTimeMillis()
                )
                viewModel.saveNote(newNote)
            }
        )
    }
}
