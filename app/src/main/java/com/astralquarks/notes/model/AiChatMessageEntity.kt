package com.astralquarks.notes.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_chat_messages",
    indices = [
        Index(value = ["noteId"]),
        Index(value = ["timestamp"])
    ]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: String? = null, // null for Global Chat, or note.id for note-specific chat
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
