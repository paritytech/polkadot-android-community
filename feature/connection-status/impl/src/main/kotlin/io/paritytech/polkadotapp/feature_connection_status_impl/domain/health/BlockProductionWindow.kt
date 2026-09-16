package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Heights rather than arrival counts: a lost head notification must not read as a stall.
@OptIn(ExperimentalTime::class)
class BlockProductionWindow(
    private val window: Duration,
    private val expectedBlocks: Int,
) {
    private val blockTime = window / expectedBlocks
    private val minObservation = blockTime * MIN_OBSERVED_BLOCKS

    private val samples = ArrayDeque<Sample>()
    private var observingSince: Instant? = null

    data class Measured(val blocks: Int, val expected: Int)

    fun restart(at: Instant) {
        samples.clear()
        observingSince = at
    }

    fun record(height: Int, at: Instant) {
        if (observingSince == null) observingSince = at

        // The clock is the wall clock and can step backwards; the window arithmetic reads the deque as
        // sorted, so an out-of-order arrival is pinned to its predecessor rather than inserted behind it.
        val orderedAt = maxOf(at, samples.lastOrNull()?.at ?: at)
        samples.addLast(Sample(height, orderedAt))
        dropExpired(orderedAt)
    }

    // chainTimeSpan is measured from the blocks' own timestamps, not the device clock.
    fun seed(headHeight: Int, chainTimeSpan: Duration, at: Instant) {
        // Repeats the data source's check: mapping an inconsistent span to a full window would report
        // a healthy chain on input already known to be wrong.
        if (chainTimeSpan <= Duration.ZERO) return

        val effectiveBlocks = when {
            chainTimeSpan <= window -> expectedBlocks
            else -> floor(expectedBlocks * (window / chainTimeSpan)).toInt().coerceIn(0, expectedBlocks)
        }
        val windowStart = at - window

        restart(windowStart)
        record(height = (headHeight - effectiveBlocks).coerceAtLeast(0), at = windowStart)
        record(height = headHeight, at = at)
    }

    // Null only for the first few block times, where nothing can be judged yet. Past that the verdict is
    // scaled to the interval measured, so a chain producing nothing is reported without waiting a window.
    fun produced(at: Instant): Measured? {
        val since = observingSince ?: return null
        if (at - since < minObservation) return null

        // The baseline sample starts the measured interval rather than counting inside it, so the
        // expectation runs from its timestamp and a chain keeping up reads as keeping up.
        val newest = samples.lastOrNull() ?: return Measured(blocks = 0, expected = expectedOver(at - since))
        val anchor = samples.lastOrNull { it.at <= at - window } ?: samples.first()
        val expected = expectedOver(at - anchor.at)
        // Heights only ever grow, except across a reorg deeper than the window, and a chain cannot do
        // better than everything it owed over the interval being measured.
        val blocks = (newest.height - anchor.height).coerceIn(0, expected)

        return Measured(blocks = blocks, expected = expected)
    }

    // Truncated, not rounded: a block half an interval away is not yet owed, and crediting it reads a
    // chain that is keeping up as falling behind.
    private fun expectedOver(elapsed: Duration): Int =
        (expectedBlocks * (elapsed.coerceAtMost(window) / window)).toInt().coerceIn(1, expectedBlocks)

    // Growth is measured from the newest sample at or before the window start, so dropping that one
    // would let a stalled chain read as live.
    private fun dropExpired(at: Instant) {
        val windowStart = at - window
        val anchorIndex = samples.indexOfLast { it.at <= windowStart }

        repeat(anchorIndex.coerceAtLeast(0)) { samples.removeFirst() }
    }

    private data class Sample(val height: Int, val at: Instant)

    private companion object {
        const val MIN_OBSERVED_BLOCKS = 3
    }
}
