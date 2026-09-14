package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A trailing window of observed chain heads, from which the number of blocks the chain produced is
 * read as the height difference across the window rather than as a count of the heads that arrived.
 * A head the subscription never delivered still lies between two heights, so delivery gaps no longer
 * read as a chain that stopped producing.
 */
@OptIn(ExperimentalTime::class)
class BlockProductionWindow(private val window: Duration) {
    private data class Sample(val height: Int, val at: Instant)

    private val samples = ArrayDeque<Sample>()

    // Kept outside the deque: a stall long enough to empty the window is exactly when the caller
    // still needs to know when the last block landed.
    private var lastArrival: Instant? = null

    fun record(height: Int, at: Instant) {
        samples.addLast(Sample(height, at))
        lastArrival = at
        prune(at)
    }

    fun clear() {
        samples.clear()
        lastArrival = null
    }

    fun lastArrival(): Instant? = lastArrival

    /**
     * Blocks produced across the window, or null while no sample reaches back to its start — the
     * span is unknown rather than empty, and a caller must not read that as a stalled chain.
     */
    fun blocksProduced(now: Instant): Int? {
        prune(now)
        val oldest = samples.firstOrNull() ?: return null
        val newest = samples.last()
        if (oldest.at > now - window) return null

        // A reorg can put the head below the anchor; the chain produced nothing we can measure.
        return (newest.height - oldest.height).coerceAtLeast(0)
    }

    /**
     * Seeds the window from a measurement of the chain's own clock so production is known at once
     * instead of after a full window of observation.
     */
    fun seed(headHeight: Int, producedInWindow: Int, at: Instant) {
        clear()
        record(height = headHeight - producedInWindow, at = at - window)
        record(height = headHeight, at = at)
        // Seeding is not an observation: a seeded window must not claim a block ever landed.
        lastArrival = null
    }

    // The newest sample at or before the window start is the anchor the height difference is
    // measured from; dropping it would let a stalled chain read as if it were still producing.
    private fun prune(now: Instant) {
        val cutoff = now - window
        while (samples.size > 1 && samples[1].at <= cutoff) samples.removeFirst()
    }
}
