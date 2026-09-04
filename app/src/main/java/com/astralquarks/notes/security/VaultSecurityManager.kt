package com.astralquarks.notes.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class VaultAuthMode {
    PASSWORD_ONLY
}

data class VaultMetaConfig(
    val kdfSalt: String,
    val wrappedVmk: String,
    val wrappedVmkIv: String,
    val verifier: String,
    val verifierIv: String,
    val updatedAt: Long
)

class VaultSecurityManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_security_prefs", Context.MODE_PRIVATE)

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val secureRandom = SecureRandom()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private var inMemoryVaultKey: SecretKey? = null

    private val _hasVault = MutableStateFlow<Boolean?>(null)
    val hasVault: StateFlow<Boolean?> = _hasVault.asStateFlow()

    private val VERIFY_TOKEN = "VAULT_VERIFY_V1"

    fun getVaultKey(): SecretKey? = inMemoryVaultKey

    fun isPasswordSet(): Boolean {
        return prefs.contains(KEY_SALT) || _hasVault.value == true
    }

    suspend fun checkVaultExists(): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            try {
                val doc = firestore.collection("user")
                    .document(uid)
                    .collection("vault_meta")
                    .document("config")
                    .get()
                    .await()
                if (doc.exists() && doc.getString("wrappedVmk") != null) {
                    _hasVault.value = true
                    prefs.edit().putBoolean(KEY_HAS_REMOTE_VAULT, true).apply()
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.e("VaultSecurityManager", "Failed to check remote vault", e)
            }
        }
        val localExists = prefs.contains(KEY_SALT) || prefs.getBoolean(KEY_HAS_REMOTE_VAULT, false)
        _hasVault.value = localExists
        localExists
    }

    suspend fun setupNewVault(password: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val salt = CryptoEngine.generateSalt()
            val kek = CryptoEngine.deriveKey(password, salt)

            val vmkBytes = ByteArray(32)
            secureRandom.nextBytes(vmkBytes)
            val vmk = SecretKeySpec(vmkBytes, "AES")

            val vmkIv = ByteArray(12)
            secureRandom.nextBytes(vmkIv)
            val vmkCipher = Cipher.getInstance("AES/GCM/NoPadding")
            vmkCipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(128, vmkIv))
            val wrappedVmk = vmkCipher.doFinal(vmkBytes)

            val verifierIv = ByteArray(12)
            secureRandom.nextBytes(verifierIv)
            val verifierCipher = Cipher.getInstance("AES/GCM/NoPadding")
            verifierCipher.init(Cipher.ENCRYPT_MODE, vmk, GCMParameterSpec(128, verifierIv))
            val verifierCiphertext = verifierCipher.doFinal(VERIFY_TOKEN.toByteArray(StandardCharsets.UTF_8))

            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val wrappedVmkB64 = Base64.encodeToString(wrappedVmk, Base64.NO_WRAP)
            val wrappedVmkIvB64 = Base64.encodeToString(vmkIv, Base64.NO_WRAP)
            val verifierB64 = Base64.encodeToString(verifierCiphertext, Base64.NO_WRAP)
            val verifierIvB64 = Base64.encodeToString(verifierIv, Base64.NO_WRAP)

            prefs.edit()
                .putString(KEY_SALT, saltB64)
                .putString(KEY_WRAPPED_VMK, wrappedVmkB64)
                .putString(KEY_WRAPPED_VMK_IV, wrappedVmkIvB64)
                .putString(KEY_VERIFIER, verifierB64)
                .putString(KEY_VERIFIER_IV, verifierIvB64)
                .putBoolean(KEY_HAS_REMOTE_VAULT, true)
                .apply()

            val uid = auth.currentUser?.uid
            if (uid != null) {
                val map = mapOf(
                    "version" to 1,
                    "kdfSalt" to saltB64,
                    "kdfIterations" to 100000,
                    "wrappedVmk" to wrappedVmkB64,
                    "wrappedVmkIv" to wrappedVmkIvB64,
                    "verifier" to verifierB64,
                    "verifierIv" to verifierIvB64,
                    "updatedAt" to System.currentTimeMillis()
                )
                firestore.collection("user")
                    .document(uid)
                    .collection("vault_meta")
                    .document("config")
                    .set(map, SetOptions.merge())
                    .await()
            }

            inMemoryVaultKey = vmk
            _isVaultUnlocked.value = true
            _hasVault.value = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("VaultSecurityManager", "setupNewVault failed", e)
            Result.failure(e)
        }
    }

    suspend fun unlockWithPassword(password: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            var config = fetchRemoteConfig()
            if (config == null) {
                config = fetchLocalConfig()
            }

            if (config == null) {
                return@withContext Result.failure(Exception("No vault configuration found. Please set up vault."))
            }

            val salt = Base64.decode(config.kdfSalt, Base64.NO_WRAP)
            val kek = CryptoEngine.deriveKey(password, salt)

            val wrappedVmk = Base64.decode(config.wrappedVmk, Base64.NO_WRAP)
            val wrappedVmkIv = Base64.decode(config.wrappedVmkIv, Base64.NO_WRAP)

            val vmkCipher = Cipher.getInstance("AES/GCM/NoPadding")
            vmkCipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, wrappedVmkIv))
            val vmkBytes = vmkCipher.doFinal(wrappedVmk)
            val vmk = SecretKeySpec(vmkBytes, "AES")

            val verifierCiphertext = Base64.decode(config.verifier, Base64.NO_WRAP)
            val verifierIv = Base64.decode(config.verifierIv, Base64.NO_WRAP)
            val verifierCipher = Cipher.getInstance("AES/GCM/NoPadding")
            verifierCipher.init(Cipher.DECRYPT_MODE, vmk, GCMParameterSpec(128, verifierIv))
            val decryptedTokenBytes = verifierCipher.doFinal(verifierCiphertext)
            val token = String(decryptedTokenBytes, StandardCharsets.UTF_8)

            if (token == VERIFY_TOKEN) {
                inMemoryVaultKey = vmk
                _isVaultUnlocked.value = true
                Result.success(true)
            } else {
                Result.failure(Exception("Incorrect vault password."))
            }
        } catch (e: Exception) {
            Log.w("VaultSecurityManager", "Vault unlock attempt failed: ${e.message}")
            Result.failure(Exception("Incorrect vault password. Try again."))
        }
    }

    suspend fun verifyPassword(password: String): Result<Boolean> = unlockWithPassword(password)

    suspend fun setPassword(password: String): Result<Unit> = setupNewVault(password)

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault() {
        inMemoryVaultKey = null
        _isVaultUnlocked.value = false
    }

    private suspend fun fetchRemoteConfig(): VaultMetaConfig? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val doc = firestore.collection("user")
                .document(uid)
                .collection("vault_meta")
                .document("config")
                .get()
                .await()
            if (doc.exists()) {
                val salt = doc.getString("kdfSalt") ?: return null
                val wrappedVmk = doc.getString("wrappedVmk") ?: return null
                val wrappedVmkIv = doc.getString("wrappedVmkIv") ?: return null
                val verifier = doc.getString("verifier") ?: return null
                val verifierIv = doc.getString("verifierIv") ?: return null
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                VaultMetaConfig(salt, wrappedVmk, wrappedVmkIv, verifier, verifierIv, updatedAt)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchLocalConfig(): VaultMetaConfig? {
        val salt = prefs.getString(KEY_SALT, null) ?: return null
        val wrappedVmk = prefs.getString(KEY_WRAPPED_VMK, null) ?: return null
        val wrappedVmkIv = prefs.getString(KEY_WRAPPED_VMK_IV, null) ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return null
        val verifierIv = prefs.getString(KEY_VERIFIER_IV, null) ?: return null
        return VaultMetaConfig(salt, wrappedVmk, wrappedVmkIv, verifier, verifierIv, System.currentTimeMillis())
    }

    companion object {
        private const val KEY_SALT = "key_vault_kdf_salt"
        private const val KEY_WRAPPED_VMK = "key_vault_wrapped_vmk"
        private const val KEY_WRAPPED_VMK_IV = "key_vault_wrapped_vmk_iv"
        private const val KEY_VERIFIER = "key_vault_verifier"
        private const val KEY_VERIFIER_IV = "key_vault_verifier_iv"
        private const val KEY_AUTH_MODE = "key_vault_auth_mode"
        private const val KEY_HAS_REMOTE_VAULT = "key_has_remote_vault"
    }
}
