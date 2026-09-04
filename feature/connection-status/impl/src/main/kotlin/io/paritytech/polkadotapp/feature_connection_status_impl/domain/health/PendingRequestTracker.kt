package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Tracks how long the oldest still-pending socket request has been waiting. Requests carry no id, so
 * they are tracked by referential identity: a request first seen keeps its timestamp until it is no
 * longer in the pending set. Not thread-safe — drive it from a single collector.
 */
class PendingRequestTracker {

    private val firstSeenMillis = IdentityHashMap<Any, Long>()

    /** Reconcile with the current [pending] set and return the age (millis) of the oldest one, or 0. */
    fun update(pending: Set<Any>, nowMillis: Long): Long {
        pending.forEach { request ->
            if (!firstSeenMillis.containsKey(request)) firstSeenMillis[request] = nowMillis
        }

        val stillPending = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply { addAll(pending) }
        firstSeenMillis.keys.retainAll { it in stillPending }

        val oldest = firstSeenMillis.values.minOrNull() ?: return 0L
        return nowMillis - oldest
    }
}
