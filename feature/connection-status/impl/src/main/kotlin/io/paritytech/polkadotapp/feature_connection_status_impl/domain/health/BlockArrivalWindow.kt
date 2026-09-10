package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Not thread-safe: drive it from a single collector.
@OptIn(ExperimentalTime::class)
class BlockArrivalWindow(private val window: Duration) {
    private val arrivals = ArrayDeque<Instant>()

    fun recordArrival(at: Instant) {
        arrivals.addLast(at)
    }

    fun pruneAndCount(now: Instant): Int {
        val cutoff = now - window
        while (arrivals.isNotEmpty() && arrivals.first() < cutoff) arrivals.removeFirst()
        return arrivals.size
    }
}
