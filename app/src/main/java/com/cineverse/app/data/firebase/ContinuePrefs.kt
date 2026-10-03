package com.cineverse.app.data.firebase

import androidx.compose.runtime.Immutable
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * The order of Continue Watching, as the website keeps it: the field
 * `continueWatching` on the user's document, `{ pinned, hidden,
 * clientUpdatedAt }`. Pinned keys lead the row in that order; everything else
 * follows in the automatic order; hidden keys are left out. Arrange the row on
 * either device and the other follows.
 */
@Immutable
data class ContinueOrder(
    val pinned: List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
    val clientUpdatedAt: Long = 0,
) {
    /** Apply to an automatically ordered row, keyed by `type_id`. */
    fun <T> apply(rows: List<T>, key: (T) -> String): List<T> {
        val visible = rows.filterNot { key(it) in hidden }
        if (pinned.isEmpty()) return visible
        val rank = pinned.withIndex().associate { (i, k) -> k to i }
        val first = visible.filter { key(it) in rank }.sortedBy { rank.getValue(key(it)) }
        return first + visible.filterNot { key(it) in rank }
    }

    companion object {
        const val CAP = 40

        /** "tv_123" / "movie_456", and the website's bare show ids as tv_. */
        fun normal(value: Any?): String? {
            val text = value?.toString().orEmpty()
            text.toLongOrNull()?.let { return if (it > 0) "tv_$it" else null }
            val match = Regex("^(tv|movie)_(\\d+)$").find(text) ?: return null
            return "${match.groupValues[1]}_${match.groupValues[2].toLong()}"
        }

        fun clean(values: Any?): List<String> =
            (values as? List<*>).orEmpty().mapNotNull(::normal).distinct().take(CAP)

        fun from(map: Map<*, *>?): ContinueOrder = ContinueOrder(
            pinned = clean(map?.get("pinned")),
            hidden = clean(map?.get("hidden")),
            clientUpdatedAt = (map?.get("clientUpdatedAt") as? Number)?.toLong() ?: 0,
        )
    }
}

class ContinuePrefsRepository(
    private val store: FirebaseFirestore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    /** An arrangement just made here, shown before the server has it. */
    private val pending = MutableStateFlow<ContinueOrder?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remote: StateFlow<ContinueOrder> = auth.uid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(ContinueOrder())
            else callbackFlow {
                val registration = store.collection("users").document(uid)
                    .addSnapshotListener { snapshot, _ ->
                        trySend(ContinueOrder.from(snapshot?.get("continueWatching") as? Map<*, *>))
                    }
                awaitClose { registration.remove() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, ContinueOrder())

    val order: StateFlow<ContinueOrder> = combine(remote, pending) { server, local ->
        if (local != null && local.clientUpdatedAt >= server.clientUpdatedAt) local else server
    }.stateIn(scope, SharingStarted.Eagerly, ContinueOrder())

    /**
     * The whole visible row, in the order chosen by hand. Written in a
     * transaction over the server copy, exactly as the website's `order`
     * operation: the new order leads, hidden keys are kept.
     */
    fun setOrder(keys: List<String>) = write { base ->
        val order = keys.mapNotNull(ContinueOrder::normal).distinct().take(ContinueOrder.CAP)
        val held = order.toSet()
        base.copy(pinned = (order + base.pinned.filterNot { it in held }).take(ContinueOrder.CAP))
    }

    /** Back to the automatic order: nothing pinned, nothing hidden. */
    fun reset() = write { ContinueOrder() }

    private fun write(change: (ContinueOrder) -> ContinueOrder) {
        val now = System.currentTimeMillis()
        val optimistic = change(order.value).copy(clientUpdatedAt = now)
        pending.value = optimistic
        scope.launch {
            val uid = auth.uid.value ?: return@launch
            val ref = store.collection("users").document(uid)
            runCatching {
                store.runTransaction { transaction ->
                    val snapshot = transaction.get(ref)
                    val base = ContinueOrder.from(snapshot.get("continueWatching") as? Map<*, *>)
                    val next = change(base).copy(clientUpdatedAt = maxOf(now, base.clientUpdatedAt))
                    transaction.set(
                        ref,
                        mapOf(
                            "continueWatching" to mapOf(
                                "pinned" to next.pinned,
                                "hidden" to next.hidden,
                                "clientUpdatedAt" to next.clientUpdatedAt,
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        ),
                        // Replace this one field whole; a deep merge would keep
                        // keys the new order meant to drop.
                        SetOptions.mergeFields("continueWatching"),
                    )
                }.await()
            }
        }
    }
}
