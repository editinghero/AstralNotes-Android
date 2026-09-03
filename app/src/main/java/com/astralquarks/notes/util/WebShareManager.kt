package com.astralquarks.notes.util

import android.util.Base64
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.security.CryptoEngine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.UUID

data class WebShareItem(
    val shareId: String,
    val ownerUid: String,
    val noteId: String,
    val title: String,
    val isPasswordProtected: Boolean,
    val createdAt: Long,
    val expiresAt: Long?,
    val shareUrl: String
)

object WebShareManager {

    private const val PUBLIC_SALT_B64 = "cHVibGljX2FzdHJhbF9zYWx0X3Yx"
    private const val PUBLIC_PASSPHRASE = "astral_public_share_token_open"
    private const val BASE_WEB_URL = "https://astralnotesweb.pages.dev/#/share/"

    suspend fun createWebShare(
        note: Note,
        password: String?,
        expiryHours: Int?
    ): Result<String> = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return@withContext Result.failure(Exception("User must be signed in to create web share"))

        try {
            val isPasswordProtected = !password.isNullOrBlank()
            val saltBytes: ByteArray
            val key = if (isPasswordProtected) {
                saltBytes = ByteArray(16)
                SecureRandom().nextBytes(saltBytes)
                CryptoEngine.deriveKey(password!!.trim(), saltBytes)
            } else {
                saltBytes = Base64.decode(PUBLIC_SALT_B64, Base64.NO_WRAP)
                CryptoEngine.deriveKey(PUBLIC_PASSPHRASE, saltBytes)
            }

            val encrypted = CryptoEngine.encryptNotePayload(
                title = note.title,
                content = note.content,
                tags = note.tags,
                imageUrls = note.imageUrls,
                secretKey = key
            )

            val shareId = UUID.randomUUID().toString()
            val createdAt = System.currentTimeMillis()
            val expiresAt = if (expiryHours != null && expiryHours > 0) {
                createdAt + (expiryHours.toLong() * 3600L * 1000L)
            } else {
                null
            }

            val saltB64 = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val shareData = hashMapOf(
                "shareId" to shareId,
                "ownerUid" to user.uid,
                "noteId" to note.id,
                "title" to note.title.ifBlank { "Untitled Note" },
                "salt" to saltB64,
                "iv" to encrypted.iv,
                "encryptedPayload" to encrypted.encryptedData,
                "createdAt" to createdAt,
                "expiresAt" to expiresAt,
                "isPasswordProtected" to isPasswordProtected
            )

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("shares").document(shareId).set(shareData).await()

            val shareUrl = "$BASE_WEB_URL$shareId"
            Result.success(shareUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserShares(): List<WebShareItem> = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return@withContext emptyList()

        try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("shares")
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val shareId = doc.getString("shareId") ?: doc.id
                val ownerUid = doc.getString("ownerUid") ?: return@mapNotNull null
                val noteId = doc.getString("noteId") ?: ""
                val title = doc.getString("title") ?: "Untitled Note"
                val isPasswordProtected = doc.getBoolean("isPasswordProtected") ?: false
                val createdAt = doc.getLong("createdAt") ?: 0L
                val expiresAt = doc.getLong("expiresAt")
                val shareUrl = "$BASE_WEB_URL$shareId"

                WebShareItem(
                    shareId = shareId,
                    ownerUid = ownerUid,
                    noteId = noteId,
                    title = title,
                    isPasswordProtected = isPasswordProtected,
                    createdAt = createdAt,
                    expiresAt = expiresAt,
                    shareUrl = shareUrl
                )
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun revokeShare(shareId: String): Boolean = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return@withContext false

        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("shares").document(shareId)
            val snap = docRef.get().await()
            if (snap.exists() && snap.getString("ownerUid") == uid) {
                docRef.delete().await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
