package com.cineverse.app.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cineverse.app.data.firebase.Firebase
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** What came back from the sheet. Cancelling is not a failure. */
sealed interface GoogleResult {
    data class Token(val idToken: String) : GoogleResult
    data object Cancelled : GoogleResult
    data class Failed(val message: String) : GoogleResult
}

/**
 * Google sign-in through Credential Manager.
 *
 * This is the current API and the only one worth writing in 2026: the old
 * `GoogleSignInClient` is deprecated, and Credential Manager is what shows the
 * bottom sheet with the accounts already on the device — no browser, no
 * redirect, and it also surfaces passkeys and saved passwords for free later.
 *
 * It is asked for TWICE on purpose. The first ask filters to accounts that have
 * used CineVerse before, which on a returning device is a one-tap sheet with the
 * right account already selected. Only if there are none does it ask again with
 * the filter off, which is the full account picker. Doing it the other way round
 * means every returning user picks from a list they do not need to see.
 */
suspend fun requestGoogleIdToken(context: Context): GoogleResult {
    if (!Firebase.googleSignInAvailable) {
        return GoogleResult.Failed("Google sign-in is not configured for this build.")
    }
    val manager = CredentialManager.create(context)

    suspend fun ask(authorizedOnly: Boolean): GoogleResult = try {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(Firebase.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            // Skips the "choose an account" step when the device has exactly one.
            .setAutoSelectEnabled(authorizedOnly)
            .build()
        val response = manager.getCredential(
            context = context,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleResult.Failed("That credential was not a Google account.")
        }
    } catch (error: GetCredentialCancellationException) {
        GoogleResult.Cancelled
    } catch (error: NoCredentialException) {
        GoogleResult.Failed(NO_ACCOUNTS)
    } catch (error: GetCredentialException) {
        GoogleResult.Failed(
            error.message?.takeIf { it.isNotBlank() } ?: "Google sign-in could not start."
        )
    }

    val first = ask(authorizedOnly = true)
    if (first !is GoogleResult.Failed || first.message != NO_ACCOUNTS) return first

    // No account has used CineVerse on this device yet: show the full picker.
    // If THAT comes back empty too, there is no Google account on the phone at
    // all, which is a different problem and needs a different sentence — the
    // first one would send someone looking for a setting that does not exist.
    val second = ask(authorizedOnly = false)
    return if (second is GoogleResult.Failed && second.message == NO_ACCOUNTS) {
        GoogleResult.Failed(
            "There is no Google account on this device. Add one in Android's settings, or sign in with an email and password."
        )
    } else {
        second
    }
}

private const val NO_ACCOUNTS = "no-google-account"
