package com.astralquarks.notes.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val colorHex: String = "#DEFAULT",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isLocked: Boolean = false,
    val isTrash: Boolean = false,
    val tags: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val reminderTime: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title,
            "content" to content,
            "colorHex" to colorHex,
            "isPinned" to isPinned,
            "isArchived" to isArchived,
            "isLocked" to isLocked,
            "isTrash" to isTrash,
            "tags" to tags,
            "imageUrls" to imageUrls,
            "reminderTime" to reminderTime,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>): Note {
            return Note(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                title = map["title"] as? String ?: "",
                content = map["content"] as? String ?: "",
                colorHex = map["colorHex"] as? String ?: "#FFFFFF",
                isPinned = map["isPinned"] as? Boolean ?: false,
                isArchived = map["isArchived"] as? Boolean ?: false,
                isLocked = map["isLocked"] as? Boolean ?: false,
                isTrash = map["isTrash"] as? Boolean ?: false,
                tags = (map["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                imageUrls = (map["imageUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                reminderTime = (map["reminderTime"] as? Number)?.toLong(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isSynced = true
            )
        }
    }
}
