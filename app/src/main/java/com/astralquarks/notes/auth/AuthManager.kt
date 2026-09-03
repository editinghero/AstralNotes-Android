package com.astralquarks.notes.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.astralquarks.notes.model.Note
import com.astralquarks.notes.security.CryptoEngine
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.crypto.SecretKey

enum class SyncStatus {
    SYNCED,
    SYNCING,
    OFFLINE_PENDING,
    ERROR
}

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val vaultPrefs = context.getSharedPreferences("vault_secure_meta", Context.MODE_PRIVATE)

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
        }
    }

    val isSignedIn: Boolean
        get() = _currentUser.value != null

    val userId: String?
        get() = _currentUser.value?.uid

    val userEmail: String?
        get() = _currentUser.value?.email

    val userDisplayName: String?
        get() = _currentUser.value?.displayName

    val userPhotoUrl: String?
        get() = _currentUser.value?.photoUrl?.toString()

    /**
     * Derives or retrieves the 256-bit AES master encryption key for this user.
     * Compatible with Web Crypto API standards using PBKDF2WithHmacSHA256.
     */
    fun getEncryptionKey(): SecretKey? {
        val uid = userId ?: return null
        val storedPass = vaultPrefs.getString("vault_key_$uid", null) ?: uid
        val saltStr = vaultPrefs.getString("vault_salt_$uid", null)
        val salt = if (!saltStr.isNullOrBlank()) {
            Base64.decode(saltStr, Base64.NO_WRAP)
        } else {
            val newSalt = CryptoEngine.generateSalt()
            vaultPrefs.edit().putString("vault_salt_$uid", Base64.encodeToString(newSalt, Base64.NO_WRAP)).apply()
            newSalt
        }
        return CryptoEngine.deriveKey(storedPass, salt)
    }

    private fun getEffectiveWebClientId(): String {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val found = context.getString(resId)
                if (found.isNotBlank()) return found.trim()
            }
        } catch (e: Exception) {
        }
        return "173977964592-mqo6ac19i53stper4p2l843bn3220qea.apps.googleusercontent.com"
    }

    suspend fun signInWithGoogle(activityContext: Context = context): Result<FirebaseUser> {
        val clientId = getEffectiveWebClientId()
        return try {
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(clientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val credential = result.credential
            val idToken = if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }

            if (!idToken.isNullOrBlank()) {
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to get Firebase User"))
                }
            } else {
                Result.failure(Exception("Unexpected credential type: ${credential.javaClass.simpleName}"))
            }
        } catch (e: GetCredentialException) {
            Log.e("AuthManager", "Google Sign-in failed", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign-in error", e)
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _currentUser.value = null
            _syncMessage.value = "Signed out"
            _syncStatus.value = SyncStatus.SYNCED
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign out error", e)
        }
    }

    /**
     * Uploads note to new V2 Firestore path: user/{uid}/notes/{noteId}
     * Sensitive fields (title, content, tags) are client-side encrypted before writing.
     * The legacy `users` collection is completely untouched.
     */
    suspend fun uploadNoteToFirestore(note: Note): Boolean {
        val uid = userId ?: return false
        return try {
            _isSyncing.value = true
            _syncStatus.value = SyncStatus.SYNCING
            val key = if (note.isLocked) getEncryptionKey() else null
            firestore.collection("user")
                .document(uid)
                .collection("notes")
                .document(note.id)
                .set(note.toFirestoreV2Map(key), SetOptions.merge())
                .await()
            _syncMessage.value = "Synced with Cloud"
            _syncStatus.value = SyncStatus.SYNCED
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Error uploading note to Firestore V2", e)
            _syncMessage.value = "Sync error: ${e.localizedMessage}"
            _syncStatus.value = SyncStatus.ERROR
            false
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Batch uploads notes to new V2 Firestore path: user/{uid}/notes
     */
    suspend fun uploadNotesBatch(notes: List<Note>): Boolean {
        val uid = userId ?: return false
        if (notes.isEmpty()) return true
        return try {
            _isSyncing.value = true
            _syncStatus.value = SyncStatus.SYNCING
            val batch = firestore.batch()
            for (note in notes) {
                val key = if (note.isLocked) getEncryptionKey() else null
                val docRef = firestore.collection("user")
                    .document(uid)
                    .collection("notes")
                    .document(note.id)
                batch.set(docRef, note.toFirestoreV2Map(key), SetOptions.merge())
            }
            batch.commit().await()
            _syncMessage.value = "Synced ${notes.size} notes"
            _syncStatus.value = SyncStatus.SYNCED
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Batch upload error V2", e)
            _syncMessage.value = "Sync error: ${e.localizedMessage}"
            _syncStatus.value = SyncStatus.ERROR
            false
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Fetches notes from new V2 Firestore path: user/{uid}/notes
     * Performs client-side decryption on fetched payloads.
     */
    suspend fun fetchCloudNotes(): List<Note> {
        val uid = userId ?: return emptyList()
        return try {
            _isSyncing.value = true
            _syncStatus.value = SyncStatus.SYNCING
            val key = getEncryptionKey()
            val snapshot = firestore.collection("user")
                .document(uid)
                .collection("notes")
                .get()
                .await()
            val result = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Note.fromFirestoreV2Map(it, key) }
            }
            _syncStatus.value = SyncStatus.SYNCED
            result
        } catch (e: Exception) {
            Log.e("AuthManager", "Error fetching cloud notes V2", e)
            _syncStatus.value = SyncStatus.OFFLINE_PENDING
            emptyList()
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Deletes note from new V2 Firestore path: user/{uid}/notes/{noteId}
     */
    suspend fun deleteNoteFromFirestore(noteId: String): Boolean {
        val uid = userId ?: return false
        return try {
            firestore.collection("user")
                .document(uid)
                .collection("notes")
                .document(noteId)
                .delete()
                .await()

            try {
                val sharesSnapshot = firestore.collection("shares")
                    .whereEqualTo("ownerUid", uid)
                    .whereEqualTo("noteId", noteId)
                    .get()
                    .await()
                for (doc in sharesSnapshot.documents) {
                    doc.reference.delete().await()
                }
            } catch (se: Exception) {
                Log.w("AuthManager", "Could not cleanup shares for note: $noteId", se)
            }

            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Error deleting note from Firestore V2", e)
            false
        }
    }

    /**
     * Observes real-time note updates from new V2 Firestore path: user/{uid}/notes
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFirestoreNotes(): Flow<List<Note>> = currentUser.flatMapLatest { user ->
        val uid = user?.uid
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                var registration: ListenerRegistration? = null
                try {
                    val key = getEncryptionKey()
                    registration = firestore.collection("user")
                        .document(uid)
                        .collection("notes")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("AuthManager", "Firestore listen error V2", error)
                                _syncStatus.value = SyncStatus.ERROR
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val notes = snapshot.documents.mapNotNull { doc ->
                                    doc.data?.let { Note.fromFirestoreV2Map(it, key) }
                                }
                                _syncStatus.value = SyncStatus.SYNCED
                                trySend(notes)
                            }
                        }
                } catch (e: Exception) {
                    Log.e("AuthManager", "Failed to attach firestore listener V2", e)
                    _syncStatus.value = SyncStatus.ERROR
                }

                awaitClose {
                    registration?.remove()
                }
            }
        }
    }
}
