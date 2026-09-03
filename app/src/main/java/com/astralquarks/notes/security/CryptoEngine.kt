package com.astralquarks.notes.security

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val encryptedData: String,
    val iv: String
)

data class DecryptedNoteContent(
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList()
)

object CryptoEngine {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100000

    private val secureRandom = SecureRandom()

    fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptNotePayload(
        title: String,
        content: String,
        tags: List<String>,
        imageUrls: List<String>,
        secretKey: SecretKey
    ): EncryptedPayload {
        val json = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("tags", JSONArray(tags))
            put("imageUrls", JSONArray(imageUrls))
        }

        val plaintext = json.toString().toByteArray(StandardCharsets.UTF_8)

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertextWithTag = cipher.doFinal(plaintext)

        val encryptedDataB64 = Base64.encodeToString(ciphertextWithTag, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return EncryptedPayload(encryptedData = encryptedDataB64, iv = ivB64)
    }

    fun decryptNotePayload(
        encryptedDataB64: String,
        ivB64: String,
        secretKey: SecretKey
    ): DecryptedNoteContent {
        val ciphertextWithTag = Base64.decode(encryptedDataB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plaintextBytes = cipher.doFinal(ciphertextWithTag)
        val jsonString = String(plaintextBytes, StandardCharsets.UTF_8)

        val json = JSONObject(jsonString)
        val title = json.optString("title", "")
        val content = json.optString("content", "")

        val tags = mutableListOf<String>()
        val tagsArray = json.optJSONArray("tags")
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }
        }

        val imageUrls = mutableListOf<String>()
        val imagesArray = json.optJSONArray("imageUrls")
        if (imagesArray != null) {
            for (i in 0 until imagesArray.length()) {
                imageUrls.add(imagesArray.getString(i))
            }
        }

        return DecryptedNoteContent(
            title = title,
            content = content,
            tags = tags,
            imageUrls = imageUrls
        )
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return salt
    }
}
