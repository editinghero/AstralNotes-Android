package com.astralquarks.notes.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.security.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.SecretKey

data class ImportResult(
    val regularNotesImported: Int,
    val vaultNotesImported: Int,
    val importedNotes: List<Note>
)

object LibraryBackupManager {

    suspend fun exportLibrary(
        context: Context,
        allNotes: List<Note>,
        vaultKey: SecretKey?,
        exportVaultPassword: String?,
        destinationUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val regularNotes = allNotes.filter { !it.isLocked }
            val vaultNotes = allNotes.filter { it.isLocked }

            val rootJson = JSONObject()
            rootJson.put("version", 1)
            rootJson.put("app", "AstralNotes")
            rootJson.put("exportedAt", System.currentTimeMillis())

            val regularArray = JSONArray()
            for (note in regularNotes) {
                val nObj = JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("colorHex", note.colorHex)
                    put("isPinned", note.isPinned)
                    put("isArchived", note.isArchived)
                    put("isLocked", false)
                    put("isTrash", note.isTrash)
                    put("tags", JSONArray(note.tags))
                    put("imageUrls", JSONArray(note.imageUrls))
                    put("reminderTime", note.reminderTime ?: JSONObject.NULL)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                }
                regularArray.put(nObj)
            }
            rootJson.put("notes", regularArray)

            val vaultArray = JSONArray()
            if (vaultNotes.isNotEmpty()) {
                if (vaultKey == null || exportVaultPassword.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Vault password is required to export locked notes."))
                }

                val exportSalt = ByteArray(16)
                SecureRandom().nextBytes(exportSalt)
                val exportKey = CryptoEngine.deriveKey(exportVaultPassword.trim(), exportSalt)
                val saltB64 = Base64.encodeToString(exportSalt, Base64.NO_WRAP)

                for (vNote in vaultNotes) {
                    val decrypted = try {
                        if (!vNote.encryptedData.isNullOrBlank() && !vNote.iv.isNullOrBlank()) {
                            CryptoEngine.decryptNotePayload(vNote.encryptedData!!, vNote.iv!!, vaultKey)
                        } else {
                            com.astralquarks.notes.security.DecryptedNoteContent(
                                title = vNote.title,
                                content = vNote.content,
                                tags = vNote.tags,
                                imageUrls = vNote.imageUrls
                            )
                        }
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception("Failed to decrypt vault note '${vNote.title}'. Please verify vault password."))
                    }

                    val reEncrypted = CryptoEngine.encryptNotePayload(
                        title = decrypted.title,
                        content = decrypted.content,
                        tags = decrypted.tags,
                        imageUrls = decrypted.imageUrls,
                        secretKey = exportKey
                    )

                    val vObj = JSONObject().apply {
                        put("id", vNote.id)
                        put("colorHex", vNote.colorHex)
                        put("isPinned", vNote.isPinned)
                        put("isArchived", vNote.isArchived)
                        put("isLocked", true)
                        put("isTrash", vNote.isTrash)
                        put("createdAt", vNote.createdAt)
                        put("updatedAt", vNote.updatedAt)
                        put("encryptedData", reEncrypted.encryptedData)
                        put("iv", reEncrypted.iv)
                        put("salt", saltB64)
                    }
                    vaultArray.put(vObj)
                }
            }
            rootJson.put("vaultNotes", vaultArray)

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    writer.write(rootJson.toString(2))
                }
            } ?: return@withContext Result.failure(Exception("Could not open destination file stream."))

            Result.success(regularNotes.size + vaultNotes.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackup(context: Context, uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not read backup file."))

            val rootJson = JSONObject(content)
            val regularCount = rootJson.optJSONArray("notes")?.length() ?: 0
            val vaultCount = rootJson.optJSONArray("vaultNotes")?.length() ?: 0
            Result.success(Pair(regularCount, vaultCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importLibrary(
        context: Context,
        uri: Uri,
        importedVaultPassword: String?,
        currentVaultKey: SecretKey?
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Could not read backup file."))

            val rootJson = JSONObject(content)
            val regularArray = rootJson.optJSONArray("notes") ?: JSONArray()
            val vaultArray = rootJson.optJSONArray("vaultNotes") ?: JSONArray()

            val importedNotes = mutableListOf<Note>()

            for (i in 0 until regularArray.length()) {
                val obj = regularArray.getJSONObject(i)
                val tagsList = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("tags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) tagsList.add(tagsArr.getString(t))
                }
                val imagesList = mutableListOf<String>()
                val imagesArr = obj.optJSONArray("imageUrls")
                if (imagesArr != null) {
                    for (m in 0 until imagesArr.length()) imagesList.add(imagesArr.getString(m))
                }

                val note = Note(
                    id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                    title = obj.optString("title", ""),
                    content = obj.optString("content", ""),
                    colorHex = obj.optString("colorHex", "#DEFAULT"),
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isLocked = false,
                    isTrash = obj.optBoolean("isTrash", false),
                    tags = tagsList,
                    imageUrls = imagesList,
                    reminderTime = if (obj.isNull("reminderTime")) null else obj.optLong("reminderTime"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
                importedNotes.add(note)
            }

            if (vaultArray.length() > 0) {
                if (importedVaultPassword.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Please provide the vault password for the imported file."))
                }

                for (i in 0 until vaultArray.length()) {
                    val vObj = vaultArray.getJSONObject(i)
                    val encData = vObj.getString("encryptedData")
                    val iv = vObj.getString("iv")
                    val saltB64 = vObj.getString("salt")

                    val saltBytes = Base64.decode(saltB64, Base64.NO_WRAP)
                    val importedKey = CryptoEngine.deriveKey(importedVaultPassword.trim(), saltBytes)

                    val decrypted = try {
                        CryptoEngine.decryptNotePayload(encData, iv, importedKey)
                    } catch (e: Exception) {
                        return@withContext Result.failure(Exception("Incorrect vault password for the imported file. Could not decrypt locked notes."))
                    }

                    val finalEncData: String?
                    val finalIv: String?
                    val finalTitle: String
                    val finalContent: String
                    val finalTags: List<String>
                    val finalImages: List<String>

                    if (currentVaultKey != null) {
                        val reEnc = CryptoEngine.encryptNotePayload(
                            title = decrypted.title,
                            content = decrypted.content,
                            tags = decrypted.tags,
                            imageUrls = decrypted.imageUrls,
                            secretKey = currentVaultKey
                        )
                        finalEncData = reEnc.encryptedData
                        finalIv = reEnc.iv
                        finalTitle = decrypted.title
                        finalContent = decrypted.content
                        finalTags = decrypted.tags
                        finalImages = decrypted.imageUrls
                    } else {
                        finalEncData = encData
                        finalIv = iv
                        finalTitle = decrypted.title
                        finalContent = decrypted.content
                        finalTags = decrypted.tags
                        finalImages = decrypted.imageUrls
                    }

                    val vaultNote = Note(
                        id = vObj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = finalTitle,
                        content = finalContent,
                        colorHex = vObj.optString("colorHex", "#DEFAULT"),
                        isPinned = vObj.optBoolean("isPinned", false),
                        isArchived = vObj.optBoolean("isArchived", false),
                        isLocked = true,
                        isTrash = vObj.optBoolean("isTrash", false),
                        tags = finalTags,
                        imageUrls = finalImages,
                        createdAt = vObj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = System.currentTimeMillis(),
                        encryptedData = finalEncData,
                        iv = finalIv,
                        isSynced = false
                    )
                    importedNotes.add(vaultNote)
                }
            }

            Result.success(
                ImportResult(
                    regularNotesImported = regularArray.length(),
                    vaultNotesImported = vaultArray.length(),
                    importedNotes = importedNotes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
