package com.cineverse.app.data.prefs

import com.cineverse.app.data.firebase.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Which stats panels are folded away, shared with the website.
 *
 * Stored exactly where and how the website stores it: the field `statsSections`
 * on `users/{uid}`, a map in which a key set to `true` means that section is
 * COLLAPSED and an absent key means open. Default open, so an account that has
 * never touched this sees no change.
 *
 * Two things this is careful about, because it writes to the same document the
 * rest of the account lives in:
 *
 *  - It writes with `SetOptions.merge()` and ONLY the `statsSections` field.
 *    Nothing else on that document is read, rewritten or touched.
 *  - It never writes on load. A fresh device reads the cloud copy and adopts
 *    it; it does not push its own empty map back and wipe a layout built on the
 *    laptop.
 */
class StatsSectionsRepository(
    private val store: FirebaseFirestore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val pending = MutableStateFlow<Set<String>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val collapsed: StateFlow<Set<String>> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptySet()) else live(uid)
        }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private fun live(uid: String) = callbackFlow<Set<String>> {
        val registration = store.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                @Suppress("UNCHECKED_CAST")
                val map = snapshot?.get("statsSections") as? Map<String, Any?>
                // Only `true` counts. The website writes nothing else, but a
                // stray value should not fold a panel away.
                trySend(map.orEmpty().filterValues { it == true }.keys.toSet())
            }
        awaitClose { registration.remove() }
    }

    fun toggle(id: String) {
        val current = pending.value ?: collapsed.value
        val next = if (id in current) current - id else current + id
        pending.value = next
        scope.launch {
            val uid = auth.uid.value ?: return@launch
            runCatching {
                store.collection("users").document(uid).set(
                    // The whole map each time, REPLACING the field. This used
                    // SetOptions.merge(), and Firestore merges nested maps key
                    // by key - so reopening a section wrote a map without its
                    // key, the old `true` survived the merge, and the section
                    // could never be opened again. mergeFields replaces this one
                    // field and still leaves the rest of the document alone.
                    mapOf("statsSections" to next.associateWith { true }),
                    SetOptions.mergeFields("statsSections"),
                ).await()
            }
            pending.value = null
        }
    }

    /** What the UI should draw right now, optimistic write included. */
    fun isCollapsed(id: String): Boolean = (pending.value ?: collapsed.value).contains(id)
}

/**
 * The website's own section ids, so a panel folded on one is folded on the
 * other. Anything the app does not have simply never appears in the map.
 */
object StatsSection {
    const val PULSE = "pulse"
    const val DIARY = "diary"
    const val TV = "tv"
    const val REWATCH = "rewatch"
    const val CRITIC = "critic"
    const val TASTE = "taste"
    const val EVOLUTION = "evolution"
    const val HEALTH = "health"
    const val FRANCHISES = "franchises"
    const val DIRECTORS = "directors"
    const val ACHIEVEMENTS = "achievements"
}
