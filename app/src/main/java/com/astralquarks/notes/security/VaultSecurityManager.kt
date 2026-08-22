package com.astralquarks.notes.security

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

enum class VaultAuthMode {
    PASSWORD_ONLY,
    BIOMETRIC_ONLY,
    PASSWORD_AND_BIOMETRIC
}

class VaultSecurityManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_security_prefs", Context.MODE_PRIVATE)

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val salt = "ExpressiveNotesSalt_2026"

    fun isPasswordSet(): Boolean {
        return prefs.contains(KEY_PASSWORD_HASH)
    }

    fun setPassword(password: String) {
        val hash = hashPassword(password)
        prefs.edit().putString(KEY_PASSWORD_HASH, hash).apply()
    }

    fun verifyPassword(password: String): Boolean {
        val storedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val computedHash = hashPassword(password)
        val matches = storedHash == computedHash
        if (matches) {
            _isVaultUnlocked.value = true
        }
        return matches
    }

    fun getAuthMode(): VaultAuthMode {
        val modeStr = prefs.getString(KEY_AUTH_MODE, VaultAuthMode.PASSWORD_AND_BIOMETRIC.name)
        return try {
            VaultAuthMode.valueOf(modeStr ?: VaultAuthMode.PASSWORD_AND_BIOMETRIC.name)
        } catch (e: Exception) {
            VaultAuthMode.PASSWORD_AND_BIOMETRIC
        }
    }

    fun setAuthMode(mode: VaultAuthMode) {
        prefs.edit().putString(KEY_AUTH_MODE, mode.name).apply()
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun unlockVault() {
        _isVaultUnlocked.value = true
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock Private Vault",
        subtitle: String = "Verify your biometric credential",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _isVaultUnlocked.value = true
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Biometric authentication failed. Try again.")
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
    }

    private fun hashPassword(password: String): String {
        val bytes = (password + salt).toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    companion object {
        private const val KEY_PASSWORD_HASH = "key_vault_password_hash"
        private const val KEY_AUTH_MODE = "key_vault_auth_mode"
    }
}
