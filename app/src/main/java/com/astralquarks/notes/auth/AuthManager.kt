package com.astralquarks.notes.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.astralquarks.notes.model.Note
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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

class AuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val credentialManager = CredentialManager.create(context)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

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

    private fun getEffectiveWebClientId(): String {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val found = context.getString(resId)
                if (found.isNotBlank()) return found.trim()
            }
        } catch (e: Exception) {
            // ignore
        }
        return ""
    }

    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        val clientId = getEffectiveWebClientId()
        if (clientId.isBlank()) {
            return Result.failure(
                Exception("Google Sign-In requires an updated google-services.json from Firebase with Google provider enabled.")
            )
        }
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
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
                Result.failure(Exception("Unexpected credential type returned"))
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
        } catch (e: Exception) {
            Log.e("AuthManager", "Sign out error", e)
        }
    }

    suspend fun uploadNoteToFirestore(note: Note): Boolean {
        val uid = userId ?: return false
        return try {
            _isSyncing.value = true
            firestore.collection("users")
                .document(uid)
                .collection("notes")
                .document(note.id)
                .set(note.toFirestoreMap(), SetOptions.merge())
                .await()
            _syncMessage.value = "Synced with Cloud"
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Error uploading note to Firestore", e)
            _syncMessage.value = "Sync error: ${e.localizedMessage}"
            false
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun uploadNotesBatch(notes: List<Note>): Boolean {
        val uid = userId ?: return false
        if (notes.isEmpty()) return true
        return try {
            _isSyncing.value = true
            val batch = firestore.batch()
            for (note in notes) {
                val docRef = firestore.collection("users")
                    .document(uid)
                    .collection("notes")
                    .document(note.id)
                batch.set(docRef, note.toFirestoreMap(), SetOptions.merge())
            }
            batch.commit().await()
            _syncMessage.value = "Synced ${notes.size} notes"
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Batch upload error", e)
            _syncMessage.value = "Sync error: ${e.localizedMessage}"
            false
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun fetchCloudNotes(): List<Note> {
        val uid = userId ?: return emptyList()
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("notes")
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { Note.fromFirestoreMap(it) }
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Error fetching cloud notes", e)
            emptyList()
        }
    }

    suspend fun deleteNoteFromFirestore(noteId: String): Boolean {
        val uid = userId ?: return false
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("notes")
                .document(noteId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Error deleting note from Firestore", e)
            false
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFirestoreNotes(): Flow<List<Note>> = currentUser.flatMapLatest { user ->
        val uid = user?.uid
        if (uid == null) {
            flowOf(emptyList())
        } else {
            callbackFlow {
                var registration: ListenerRegistration? = null
                try {
                    registration = firestore.collection("users")
                        .document(uid)
                        .collection("notes")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("AuthManager", "Firestore listen error", error)
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val notes = snapshot.documents.mapNotNull { doc ->
                                    doc.data?.let { Note.fromFirestoreMap(it) }
                                }
                                trySend(notes)
                            }
                        }
                } catch (e: Exception) {
                    Log.e("AuthManager", "Failed to attach firestore listener", e)
                }

                awaitClose {
                    registration?.remove()
                }
            }
        }
    }
}
