package com.cineverse.app.data.lock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A PIN on a custom list, ported from the website's js/list-lock.js.
 *
 * The hash is computed exactly as the website computes it - PBKDF2 with
 * HMAC-SHA256, 150,000 rounds, 256 bits, salted with "cineverse:" followed by
 * the list's random salt - so a PIN set on either one opens the list on both.
 *
 * Honest about what it is: a privacy screen against someone glancing at your
 * phone, not encryption. The titles stay in your own account.
 */
object ListLocks {
    const val ITERATIONS = 150_000
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValidPin(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** The hex digest the website stores. Slow by design, so off the main thread. */
    suspend fun derive(pin: String, salt: String): String = withContext(Dispatchers.Default) {
        val spec = PBEKeySpec(pin.toCharArray(), "cineverse:$salt".toByteArray(Charsets.UTF_8), ITERATIONS, 256)
        try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                .joinToString("") { "%02x".format(it) }
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant time, so a wrong PIN cannot be guessed digit by digit from timing. */
    fun sameDigest(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    suspend fun verify(pin: String, salt: String, hash: String): Boolean =
        isValidPin(pin) && sameDigest(derive(pin, salt), hash)
}

/**
 * Which locked lists are open right now. Session only, never persisted: the
 * website re-locks on every reload, and the app re-locks every time it starts.
 */
class UnlockedLists {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun unlock(id: String) = _ids.update { it + id }
    fun lock(id: String) = _ids.update { it - id }
    fun lockAll() { _ids.value = emptySet() }
}
