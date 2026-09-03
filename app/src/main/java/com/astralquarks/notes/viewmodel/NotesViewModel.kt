package com.astralquarks.notes.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astralquarks.notes.ai.GeminiManager
import com.astralquarks.notes.auth.AuthManager
import com.astralquarks.notes.db.AppDatabase
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.repository.NoteRepository
import com.astralquarks.notes.security.VaultSecurityManager
import com.astralquarks.notes.ui.components.DrawerDestination
import com.astralquarks.notes.widget.QuickNoteWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val authManager = AuthManager(application)
    val vaultSecurityManager = VaultSecurityManager(application)
    val geminiManager = GeminiManager(application)
    val repository = NoteRepository(db.noteDao(), authManager, viewModelScope)

    val activeNotes: StateFlow<List<Note>> = repository.activeNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedNotes: StateFlow<List<Note>> = repository.pinnedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unpinnedNotes: StateFlow<List<Note>> = repository.unpinnedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedNotes: StateFlow<List<Note>> = repository.archivedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashNotes: StateFlow<List<Note>> = repository.trashNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lockedNotes: StateFlow<List<Note>> = repository.lockedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isVaultUnlocked: StateFlow<Boolean> = vaultSecurityManager.isVaultUnlocked

    private val appPrefs: SharedPreferences =
        application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTagsFilter = MutableStateFlow<Set<String>>(emptySet())
    val selectedTagsFilter: StateFlow<Set<String>> = _selectedTagsFilter.asStateFlow()

    private val _currentDestination = MutableStateFlow(DrawerDestination.NOTES)
    val currentDestination: StateFlow<DrawerDestination> = _currentDestination.asStateFlow()

    init {
        // Observe user sign-in state to automatically trigger full bidirectional sync
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                vaultSecurityManager.lockVault()
                if (user != null) {
                    vaultSecurityManager.checkVaultExists()
                    repository.syncAllWithCloud()
                    QuickNoteWidgetProvider.updateAllWidgets(application)
                }
            }
        }

        // Observe real-time cloud sync updates from Firestore
        viewModelScope.launch {
            authManager.observeFirestoreNotes().collect { cloudNotes ->
                if (cloudNotes.isNotEmpty()) {
                    repository.syncCloudNotes(cloudNotes)
                    QuickNoteWidgetProvider.updateAllWidgets(application)
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleTagFilter(tag: String) {
        val current = _selectedTagsFilter.value.toMutableSet()
        if (current.contains(tag)) {
            current.remove(tag)
        } else {
            current.add(tag)
        }
        _selectedTagsFilter.value = current
    }

    fun setSingleTagFilter(tag: String) {
        _selectedTagsFilter.value = setOf(tag)
    }

    fun clearTagFilters() {
        _selectedTagsFilter.value = emptySet()
    }

    fun setDestination(destination: DrawerDestination) {
        _currentDestination.value = destination
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            repository.saveNote(note)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.togglePin(note)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun toggleArchive(note: Note) {
        viewModelScope.launch {
            repository.toggleArchive(note)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch {
            repository.moveToTrash(note)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun permanentlyDelete(note: Note) {
        viewModelScope.launch {
            repository.permanentlyDelete(note.id)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun clearTrash() {
        viewModelScope.launch {
            repository.clearTrash()
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun toggleLock(note: Note) {
        viewModelScope.launch {
            repository.setNoteLockState(note, !note.isLocked)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun updateNoteColor(note: Note, hex: String) {
        viewModelScope.launch {
            repository.updateNoteColor(note, hex)
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun renameTag(oldTag: String, newTag: String) {
        viewModelScope.launch {
            val all = repository.getAllActiveNotesList()
            all.forEach { note ->
                if (note.tags.contains(oldTag)) {
                    val updatedTags = note.tags.map { if (it == oldTag) newTag else it }.distinct()
                    repository.saveNote(note.copy(tags = updatedTags))
                }
            }
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun deleteTag(tag: String) {
        viewModelScope.launch {
            val all = repository.getAllActiveNotesList()
            all.forEach { note ->
                if (note.tags.contains(tag)) {
                    val updatedTags = note.tags.filter { it != tag }
                    repository.saveNote(note.copy(tags = updatedTags))
                }
            }
            val currentFilter = _selectedTagsFilter.value.toMutableSet()
            currentFilter.remove(tag)
            _selectedTagsFilter.value = currentFilter
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    fun triggerBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        vaultSecurityManager.showBiometricPrompt(
            activity = activity,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun relockVault() {
        vaultSecurityManager.lockVault()
    }

    fun manualSyncAll() {
        viewModelScope.launch {
            repository.syncAllWithCloud()
            QuickNoteWidgetProvider.updateAllWidgets(application)
        }
    }

    // --- AI Chat History Database Access ---
    fun getChatMessages(noteId: String?): Flow<List<com.astralquarks.notes.model.AiChatMessageEntity>> {
        return if (noteId == null) {
            repository.getAllChatMessages()
        } else {
            repository.getChatMessagesForNote(noteId)
        }
    }

    fun getAllNotesChatMessages(): Flow<List<com.astralquarks.notes.model.AiChatMessageEntity>> {
        return repository.getAllChatMessages()
    }

    fun saveChatMessage(noteId: String?, isUser: Boolean, text: String) {
        viewModelScope.launch {
            repository.insertChatMessage(noteId, isUser, text)
        }
    }

    fun clearChatHistory(noteId: String?) {
        viewModelScope.launch {
            repository.clearChatMessages(noteId)
        }
    }
}
