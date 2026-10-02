package com.cineverse.app.data.firebase

import androidx.compose.runtime.Immutable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await

@Immutable
data class CvUser(
    val uid: String,
    val email: String,
    val name: String,
    val photo: String,
    val verified: Boolean,
)

/** What went wrong, in words a person can act on. */
sealed interface AuthResult {
    data object Ok : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Sign-in, against the website's own Firebase project, so an account made on
 * either one works on both.
 *
 * Email and password always work. Google sign-in needs an Android app
 * registered in the Firebase console with this APK's signing certificate, and
 * the app only shows the button when [Firebase.googleSignInAvailable] says the
 * client id has been filled in — an offer that cannot succeed is worse than no
 * offer.
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val store: FirebaseFirestore,
    scope: CoroutineScope,
) {
    private val userFlow: Flow<CvUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { instance ->
            trySend(instance.currentUser?.let {
                CvUser(
                    uid = it.uid,
                    email = it.email.orEmpty(),
                    name = it.displayName?.takeIf(String::isNotBlank)
                        ?: it.email?.substringBefore('@').orEmpty(),
                    photo = it.photoUrl?.toString().orEmpty(),
                    verified = it.isEmailVerified,
                )
            })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val user: StateFlow<CvUser?> =
        userFlow.stateIn(scope, SharingStarted.Eagerly, auth.currentUser?.let {
            CvUser(it.uid, it.email.orEmpty(), it.displayName.orEmpty(), it.photoUrl?.toString().orEmpty(), it.isEmailVerified)
        })

    val uid: StateFlow<String?> =
        user.map { it?.uid }.stateIn(scope, SharingStarted.Eagerly, auth.currentUser?.uid)

    val signedIn: Boolean get() = user.value != null

    suspend fun signIn(email: String, password: String): AuthResult = guard {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String, name: String): AuthResult = guard {
        val credential = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val profile = com.google.firebase.auth.userProfileChangeRequest { displayName = name.trim() }
        credential.user?.updateProfile(profile)?.await()
        credential.user?.uid?.let { publishProfile(it, name.trim(), email.trim()) }
    }

    suspend fun signInWithGoogle(idToken: String): AuthResult = guard {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user?.let { publishProfile(it.uid, it.displayName.orEmpty(), it.email.orEmpty()) }
    }

    suspend fun sendPasswordReset(email: String): AuthResult = guard {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /**
     * The discoverable half of an account: name, avatar and the friend code the
     * website's "add by code" searches. Written on sign-up and on every Google
     * sign-in, merged so it never clobbers a code the website already issued.
     */
    private suspend fun publishProfile(uid: String, name: String, email: String) {
        runCatching {
            store.collection("publicProfiles").document(uid).set(
                mapOf(
                    "name" to name.ifBlank { email.substringBefore('@') },
                    "updatedAt" to System.currentTimeMillis(),
                ),
                SetOptions.merge(),
            ).await()
            // email -> uid, so a friend can add by address. The rules only let a
            // user point an entry at themselves.
            val emailKey = email.lowercase().replace(Regex("[^a-z0-9]"), "_")
            if (emailKey.isNotBlank()) {
                store.collection("emailIndex").document(emailKey)
                    .set(mapOf("uid" to uid), SetOptions.merge()).await()
            }
        }
    }

    private suspend inline fun guard(block: () -> Unit): AuthResult = try {
        block(); AuthResult.Ok
    } catch (error: Throwable) {
        AuthResult.Failed(message(error))
    }

    private fun message(error: Throwable): String = when (error) {
        is FirebaseAuthWeakPasswordException -> "That password is too short — six characters or more."
        is FirebaseAuthInvalidCredentialsException -> "That email and password do not match an account."
        is FirebaseAuthInvalidUserException -> "No account with that email."
        is FirebaseAuthUserCollisionException -> "There is already an account with that email."
        else -> error.localizedMessage?.takeIf { it.isNotBlank() }
            ?: "Could not reach the account service. Check your connection."
    }
}
