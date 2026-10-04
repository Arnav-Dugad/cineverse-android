package com.cineverse.app.data.ai

import android.content.Context
import com.cineverse.app.AppContainer
import kotlinx.coroutines.tasks.await
import java.time.YearMonth

/**
 * "Your taste in one paragraph", on Stats: Gemini reads the viewer's whole
 * brief and writes about them in the second person - what they reach for,
 * what they avoid, how their taste has moved - once a month. The paragraph
 * is kept on the device and on the account (a field on the user's own
 * document), so it is the same on every phone until the month turns.
 */
class TasteParagraph(private val app: AppContainer) {

    private val prefs = app.context.getSharedPreferences("taste_paragraph", Context.MODE_PRIVATE)

    private fun month() = YearMonth.now().toString()

    /** This month's paragraph if it has been written, here or on another device. */
    suspend fun cached(): String? {
        prefs.getString(month(), null)?.let { return it }
        val uid = app.auth.uid.value ?: return null
        return runCatching {
            val doc = com.cineverse.app.data.firebase.Firebase.firestore(app.context)
                .collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val saved = doc.get("tasteParagraph") as? Map<String, Any?>
            (saved?.get("text") as? String)?.takeIf { saved["month"] == month() && it.isNotBlank() }
                ?.also { prefs.edit().putString(month(), it).apply() }
        }.getOrNull()
    }

    /** Written as it streams: each emission is the paragraph so far. */
    fun write(): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        val brief = app.persona.brief()
        if (brief.isBlank()) return@flow
        val prompt = buildString {
            appendLine("Write one paragraph, 80 to 110 words, describing this viewer's taste in film and TV, speaking to them as \"you\".")
            appendLine("Be specific and perceptive, like a friend who knows them well: what they reach for, the people and eras they")
            appendLine("keep returning to, what they tend to avoid, and one surprising pattern or contradiction. Warm, a little witty,")
            appendLine("never flattering for its own sake. Plain prose, no lists, no headings, no greeting, never their name.")
            appendLine(Mentions.INSTRUCTION)
            appendLine()
            appendLine(brief)
        }
        var last = ""
        app.gemini.stream(prompt).collect { text ->
            last = text
            emit(text)
        }
        if (last.isNotBlank()) save(last.trim())
    }

    private fun save(text: String) {
        val month = month()
        prefs.edit().putString(month, text).apply()
        val uid = app.auth.uid.value ?: return
        runCatching {
            com.cineverse.app.data.firebase.Firebase.firestore(app.context)
                .collection("users").document(uid)
                .set(mapOf("tasteParagraph" to mapOf("month" to month, "text" to text)), com.google.firebase.firestore.SetOptions.merge())
        }
    }
}
