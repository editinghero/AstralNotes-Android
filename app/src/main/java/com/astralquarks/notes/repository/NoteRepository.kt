package com.astralquarks.notes.repository

import com.astralquarks.notes.auth.AuthManager
import com.astralquarks.notes.db.NoteDao
import com.astralquarks.notes.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteRepository(
    private val noteDao: NoteDao,
    private val authManager: AuthManager,
    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val activeNotes: Flow<List<Note>> = noteDao.getAllActiveNotes()
    val pinnedNotes: Flow<List<Note>> = noteDao.getPinnedActiveNotes()
    val unpinnedNotes: Flow<List<Note>> = noteDao.getUnpinnedActiveNotes()
    val archivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes()
    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes()
    val lockedNotes: Flow<List<Note>> = noteDao.getLockedNotes()

    fun searchNotes(query: String): Flow<List<Note>> = noteDao.searchNotes(query)

    suspend fun getNoteById(id: String): Note? = noteDao.getNoteById(id)

    fun getAllActiveNotesList(): List<Note> = noteDao.getAllActiveNotesSync()

    suspend fun getAllNotes(): List<Note> = noteDao.getAllNotes()

    fun getNoteByIdFlow(id: String): Flow<Note?> = noteDao.getNoteByIdFlow(id)

    suspend fun saveNote(note: Note) {
        val updatedNote = note.copy(updatedAt = System.currentTimeMillis())
        noteDao.insertNote(updatedNote)
        if (authManager.isSignedIn) {
            repositoryScope.launch {
                authManager.uploadNoteToFirestore(updatedNote)
            }
        }
    }

    suspend fun togglePin(note: Note) {
        val updated = note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
        saveNote(updated)
    }

    suspend fun toggleArchive(note: Note) {
        val updated = note.copy(
            isArchived = !note.isArchived,
            isPinned = if (!note.isArchived) false else note.isPinned,
            updatedAt = System.currentTimeMillis()
        )
        saveNote(updated)
    }

    suspend fun moveToTrash(note: Note) {
        val updated = note.copy(isTrash = true, isPinned = false, updatedAt = System.currentTimeMillis())
        saveNote(updated)
    }

    suspend fun restoreFromTrash(note: Note) {
        val updated = note.copy(isTrash = false, updatedAt = System.currentTimeMillis())
        saveNote(updated)
    }

    suspend fun permanentlyDelete(noteId: String) {
        noteDao.deleteNoteById(noteId)
        if (authManager.isSignedIn) {
            repositoryScope.launch {
                authManager.deleteNoteFromFirestore(noteId)
            }
        }
    }

    suspend fun clearTrash() {
        val trashNotes = noteDao.getTrashNotesSync()
        noteDao.clearTrash()
        if (authManager.isSignedIn) {
            repositoryScope.launch {
                trashNotes.forEach { note ->
                    authManager.deleteNoteFromFirestore(note.id)
                }
            }
        }
    }

    suspend fun setNoteLockState(note: Note, isLocked: Boolean) {
        val updated = note.copy(
            isLocked = isLocked,
            isPinned = if (isLocked) false else note.isPinned,
            updatedAt = System.currentTimeMillis()
        )
        saveNote(updated)
    }

    suspend fun updateNoteColor(note: Note, hex: String) {
        val updated = note.copy(colorHex = hex, updatedAt = System.currentTimeMillis())
        saveNote(updated)
    }

    suspend fun addTagToNote(note: Note, tag: String) {
        if (!note.tags.contains(tag)) {
            val updated = note.copy(tags = note.tags + tag, updatedAt = System.currentTimeMillis())
            saveNote(updated)
        }
    }

    suspend fun removeTagFromNote(note: Note, tag: String) {
        val updated = note.copy(tags = note.tags.filter { it != tag }, updatedAt = System.currentTimeMillis())
        saveNote(updated)
    }

    suspend fun syncCloudNotes(cloudNotes: List<Note>) {
        if (cloudNotes.isEmpty()) return
        withContext(Dispatchers.IO) {
            val localNotesMap = noteDao.getAllNotes().associateBy { it.id }
            val toInsertOrUpdate = mutableListOf<Note>()
            val toUpload = mutableListOf<Note>()

            for (cloudNote in cloudNotes) {
                val localNote = localNotesMap[cloudNote.id]
                if (localNote == null) {
                    toInsertOrUpdate.add(cloudNote)
                } else if (cloudNote.updatedAt > localNote.updatedAt) {
                    toInsertOrUpdate.add(cloudNote)
                } else if (localNote.updatedAt > cloudNote.updatedAt) {
                    toUpload.add(localNote)
                }
            }

            if (toInsertOrUpdate.isNotEmpty()) {
                noteDao.insertNotes(toInsertOrUpdate)
            }
            if (toUpload.isNotEmpty()) {
                authManager.uploadNotesBatch(toUpload)
            }
        }
    }

    suspend fun syncAllWithCloud(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!authManager.isSignedIn) {
            return@withContext Result.failure(Exception("User is not signed in"))
        }
        try {
            val localNotes = noteDao.getAllNotes()
            val cloudNotes = authManager.fetchCloudNotes()

            val cloudMap = cloudNotes.associateBy { it.id }
            val localMap = localNotes.associateBy { it.id }

            val toUpload = mutableListOf<Note>()
            val toSaveLocally = mutableListOf<Note>()

            for (local in localNotes) {
                val cloud = cloudMap[local.id]
                if (cloud == null || local.updatedAt > cloud.updatedAt) {
                    toUpload.add(local)
                }
            }

            for (cloud in cloudNotes) {
                val local = localMap[cloud.id]
                if (local == null || cloud.updatedAt > local.updatedAt) {
                    toSaveLocally.add(cloud)
                }
            }

            if (toSaveLocally.isNotEmpty()) {
                noteDao.insertNotes(toSaveLocally)
            }
            if (toUpload.isNotEmpty()) {
                authManager.uploadNotesBatch(toUpload)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- AI Chat History Persistence ---
    fun getChatMessagesForNote(noteId: String): Flow<List<com.astralquarks.notes.model.AiChatMessageEntity>> =
        noteDao.getChatMessagesForNote(noteId)

    fun getGlobalChatMessages(): Flow<List<com.astralquarks.notes.model.AiChatMessageEntity>> =
        noteDao.getGlobalChatMessages()

    fun getAllChatMessages(): Flow<List<com.astralquarks.notes.model.AiChatMessageEntity>> =
        noteDao.getAllChatMessages()

    suspend fun insertChatMessage(noteId: String?, isUser: Boolean, text: String): Long {
        return noteDao.insertChatMessage(
            com.astralquarks.notes.model.AiChatMessageEntity(
                noteId = noteId,
                isUser = isUser,
                text = text,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearChatMessages(noteId: String?) {
        if (noteId == null) {
            noteDao.clearGlobalChatMessages()
        } else {
            noteDao.clearChatMessagesForNote(noteId)
        }
    }
}
