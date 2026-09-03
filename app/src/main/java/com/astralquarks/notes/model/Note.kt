package com.astralquarks.notes.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.astralquarks.notes.security.CryptoEngine
import java.util.UUID
import javax.crypto.SecretKey

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
    val isSynced: Boolean = false,
    val revision: Long = 1L,
    val deviceId: String = "",
    val isDeleted: Boolean = false
) {
    /**
     * Serializes note to V2 Firestore schema.
     * When secretKey is provided, title, content, tags, and imageUrls are encrypted.
     */
    fun toFirestoreV2Map(secretKey: SecretKey? = null): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "revision" to revision,
            "deviceId" to deviceId,
            "colorHex" to colorHex,
            "isPinned" to isPinned,
            "isArchived" to isArchived,
            "isLocked" to isLocked,
            "isTrash" to isTrash,
            "isDeleted" to isDeleted,
            "reminderTime" to reminderTime,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )

        if (secretKey != null) {
            try {
                val payload = CryptoEngine.encryptNotePayload(title, content, tags, imageUrls, secretKey)
                map["isEncrypted"] = true
                map["encryptedData"] = payload.encryptedData
                map["iv"] = payload.iv
            } catch (e: Exception) {
                map["isEncrypted"] = false
                map["title"] = title
                map["content"] = content
                map["tags"] = tags
                map["imageUrls"] = imageUrls
            }
        } else {
            map["isEncrypted"] = false
            map["title"] = title
            map["content"] = content
            map["tags"] = tags
            map["imageUrls"] = imageUrls
        }

        return map
    }

    /**
     * Legacy map representation for backwards compatibility.
     */
    fun toFirestoreMap(): Map<String, Any?> = toFirestoreV2Map(null)

    companion object {
        fun fromFirestoreV2Map(map: Map<String, Any?>, secretKey: SecretKey? = null): Note {
            val id = map["id"] as? String ?: UUID.randomUUID().toString()
            val colorHex = map["colorHex"] as? String ?: "#DEFAULT"
            val isPinned = map["isPinned"] as? Boolean ?: false
            val isArchived = map["isArchived"] as? Boolean ?: false
            val isLocked = map["isLocked"] as? Boolean ?: false
            val isTrash = map["isTrash"] as? Boolean ?: false
            val isDeleted = map["isDeleted"] as? Boolean ?: false
            val revision = (map["revision"] as? Number)?.toLong() ?: 1L
            val deviceId = map["deviceId"] as? String ?: ""
            val reminderTime = (map["reminderTime"] as? Number)?.toLong()
            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

            val isEncrypted = map["isEncrypted"] as? Boolean ?: false
            val encryptedData = map["encryptedData"] as? String
            val iv = map["iv"] as? String

            var title = map["title"] as? String ?: ""
            var content = map["content"] as? String ?: ""
            var tags = (map["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            var imageUrls = (map["imageUrls"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            if (isEncrypted && !encryptedData.isNullOrBlank() && !iv.isNullOrBlank() && secretKey != null) {
                try {
                    val decrypted = CryptoEngine.decryptNotePayload(encryptedData, iv, secretKey)
                    title = decrypted.title
                    content = decrypted.content
                    tags = decrypted.tags
                    imageUrls = decrypted.imageUrls
                } catch (e: Exception) {
                    // If decryption key does not match, retain placeholder without crashing
                    title = "[Encrypted Note]"
                    content = "Unable to decrypt content with current vault key."
                    tags = emptyList()
                    imageUrls = emptyList()
                }
            } else if (isEncrypted) {
                // If it is encrypted but no key was provided or payload is missing, clear tags to avoid leak
                title = "[Locked Note]"
                content = "Unlock your private vault to view this encrypted note."
                tags = emptyList()
                imageUrls = emptyList()
            } else if (isLocked && secretKey == null) {
                // Also protect tags for legacy format or unexpected states if vault is locked
                title = "[Locked Note]"
                content = "Unlock your private vault to view this encrypted note."
                tags = emptyList()
                imageUrls = emptyList()
            }

            return Note(
                id = id,
                title = title,
                content = content,
                colorHex = colorHex,
                isPinned = isPinned,
                isArchived = isArchived,
                isLocked = isLocked,
                isTrash = isTrash,
                tags = tags,
                imageUrls = imageUrls,
                reminderTime = reminderTime,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isSynced = true,
                revision = revision,
                deviceId = deviceId,
                isDeleted = isDeleted
            )
        }

        fun fromFirestoreMap(map: Map<String, Any?>): Note = fromFirestoreV2Map(map, null)
    }
}
