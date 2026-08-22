package com.astralquarks.notes.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.astralquarks.notes.model.AiChatMessageEntity
import com.astralquarks.notes.model.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrash = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrash = 0 AND isLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotesSync(): List<Note>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE isPinned = 1 AND isArchived = 0 AND isTrash = 0 AND isLocked = 0 ORDER BY updatedAt DESC")
    fun getPinnedActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isPinned = 0 AND isArchived = 0 AND isTrash = 0 AND isLocked = 0 ORDER BY updatedAt DESC")
    fun getUnpinnedActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrash = 0 AND isLocked = 0 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrash = 1 ORDER BY updatedAt DESC")
    fun getTrashNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrash = 1")
    suspend fun getTrashNotesSync(): List<Note>

    @Query("SELECT * FROM notes WHERE isLocked = 1 AND isTrash = 0 ORDER BY updatedAt DESC")
    fun getLockedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteByIdFlow(id: String): Flow<Note?>

    @Query("SELECT * FROM notes WHERE isTrash = 0 AND isLocked = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchNotes(query: String): Flow<List<Note>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<Note>)

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: String)

    @Query("DELETE FROM notes WHERE isTrash = 1")
    suspend fun clearTrash()

    @Query("SELECT COUNT(*) FROM notes WHERE isTrash = 0 AND isLocked = 0")
    fun getActiveNotesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isLocked = 1 AND isTrash = 0")
    fun getLockedNotesCount(): Flow<Int>

    // --- AI Chat History Database Operations ---
    @Query("SELECT * FROM ai_chat_messages WHERE noteId = :noteId ORDER BY timestamp ASC")
    fun getChatMessagesForNote(noteId: String): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE noteId IS NULL ORDER BY timestamp ASC")
    fun getGlobalChatMessages(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<AiChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages WHERE noteId = :noteId")
    suspend fun clearChatMessagesForNote(noteId: String)

    @Query("DELETE FROM ai_chat_messages WHERE noteId IS NULL")
    suspend fun clearGlobalChatMessages()

    @Query("DELETE FROM ai_chat_messages")
    suspend fun clearAllChatMessages()
}
