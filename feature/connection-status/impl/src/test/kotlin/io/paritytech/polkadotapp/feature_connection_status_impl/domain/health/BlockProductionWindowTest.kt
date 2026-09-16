package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BlockProductionWindowTest {
    @Test
    fun `says nothing until a few block times have passed`() {
        val window = peopleWindow()
        window.restart(T0)

        assertNull(window.produced(T0 + 5.seconds))
        assertEquals(measured(0, 3), window.produced(T0 + 6.seconds))
    }

    @Test
    fun `counts how far the height grew across the window`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 115, at = T0 + 30.seconds)

        assertEquals(measured(blocks = 15, expected = 15), window.produced(T0 + 30.seconds))
    }

    @Test
    fun `the window slides rather than restarting`() {
        val window = peopleWindow()
        listOf(100, 105, 110, 115).forEachIndexed { step, height ->
            window.record(height = height, at = T0 + (step * 10).seconds)
        }

        assertEquals(measured(15, 15), window.produced(T0 + 30.seconds))
        assertEquals(measured(10, 15), window.produced(T0 + 40.seconds))
        assertEquals(measured(5, 15), window.produced(T0 + 50.seconds))
    }

    @Test
    fun `a chain keeping up is judged against what it owed so far, not a whole window`() {
        val window = peopleWindow()
        window.restart(T0)
        window.record(height = 100, at = T0 + 2.seconds)
        window.record(height = 101, at = T0 + 4.seconds)
        window.record(height = 102, at = T0 + 6.seconds)

        assertEquals(measured(blocks = 2, expected = 2), window.produced(T0 + 6.seconds))
    }

    @Test
    fun `a chain keeping up is not marked down between two of its blocks`() {
        val window = peopleWindow()
        window.restart(T0)
        window.record(height = 100, at = T0 + 2.seconds)
        window.record(height = 101, at = T0 + 4.seconds)
        window.record(height = 102, at = T0 + 6.seconds)

        // Five seconds after the baseline block, only two more were due and both arrived.
        assertEquals(measured(blocks = 2, expected = 2), window.produced(T0 + 7.seconds))
        assertEquals(measured(blocks = 2, expected = 2), window.produced(T0 + 7500.milliseconds))
    }

    @Test
    fun `a chain producing nothing is reported after a few block times, not a whole window`() {
        val window = peopleWindow()
        window.restart(T0)

        assertEquals(measured(blocks = 0, expected = 3), window.produced(T0 + 6.seconds))
        assertEquals(measured(blocks = 0, expected = 5), window.produced(T0 + 10.seconds))
        assertEquals(measured(blocks = 0, expected = 15), window.produced(T0 + 10.minutes))
    }

    @Test
    fun `a chain that stops after one block still reports the stall`() {
        val window = peopleWindow()
        window.restart(T0)
        window.record(height = 100, at = T0 + 2.seconds)

        assertEquals(measured(blocks = 0, expected = 4), window.produced(T0 + 10.seconds))
    }

    @Test
    fun `a stall decays to zero and recovers when blocks return`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 115, at = T0 + 30.seconds)

        assertEquals(measured(0, 15), window.produced(T0 + 60.seconds))

        window.record(height = 121, at = T0 + 66.seconds)
        assertEquals(measured(6, 15), window.produced(T0 + 66.seconds))
    }

    @Test
    fun `the sample the growth is measured from survives eviction`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 101, at = T0 + 1.seconds)
        window.record(height = 101, at = T0 + 90.seconds)

        assertEquals(measured(0, 15), window.produced(T0 + 90.seconds))
    }

    @Test
    fun `a reorg below the window start counts as no production rather than negative`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 115, at = T0 + 30.seconds)
        window.record(height = 50, at = T0 + 31.seconds)

        assertEquals(0, window.produced(T0 + 31.seconds)?.blocks)
    }

    // A stale replayed head followed by the real one lands a whole window of growth inside an interval
    // that has only owed a block or two, and an unclamped share renders as "Live 1500%".
    @Test
    fun `a height jump early in a fresh window cannot report more than everything owed`() {
        val window = peopleWindow()
        window.restart(T0)
        window.record(height = 1_000, at = T0 + 6.seconds)
        window.record(height = 1_060, at = T0 + 7.seconds)

        assertEquals(measured(blocks = 1, expected = 1), window.produced(T0 + 7.seconds))
    }

    @Test
    fun `growth beyond the expected count is capped`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 200, at = T0 + 30.seconds)

        assertEquals(measured(15, 15), window.produced(T0 + 30.seconds))
    }

    // Out of order, the newest sample can itself satisfy "at or before the window start" and become the
    // anchor its own growth is measured from, reporting nothing produced on a chain that is producing.
    @Test
    fun `a sample timed before its predecessor does not reorder the window`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 110, at = T0 + 30.seconds)
        window.record(height = 112, at = T0 + 1.seconds)

        assertEquals(12, window.produced(T0 + 31.seconds)?.blocks)
    }

    @Test
    fun `an anchor within the window fills it completely`() {
        val window = peopleWindow()
        window.seed(headHeight = 1000, chainTimeSpan = 20.seconds, at = T0)

        assertEquals(measured(15, 15), window.produced(T0))
    }

    @Test
    fun `an anchor slower than the window fills only the matching fraction`() {
        val window = peopleWindow()
        window.seed(headHeight = 1000, chainTimeSpan = 5.minutes, at = T0)

        assertEquals(measured(1, 15), window.produced(T0))
    }

    @Test
    fun `a slow bulletin chain shows its share at once instead of after a full window`() {
        val window = BlockProductionWindow(blockTime = 6.seconds, expectedBlocks = 10)
        window.seed(headHeight = 1000, chainTimeSpan = 10.minutes, at = T0)

        assertEquals(measured(1, 10), window.produced(T0))
    }

    @Test
    fun `a chain shorter than the window anchors at its genesis`() {
        val window = peopleWindow()
        window.seed(headHeight = 5, chainTimeSpan = 20.seconds, at = T0)

        assertEquals(measured(5, 15), window.produced(T0))
    }

    @Test
    fun `an inconsistent span seeds nothing rather than reporting a healthy chain`() {
        val window = peopleWindow()
        window.seed(headHeight = 1000, chainTimeSpan = Duration.ZERO, at = T0)

        assertNull(window.produced(T0))
    }

    @Test
    fun `an anchor replaces what was observed before it`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 115, at = T0 + 30.seconds)
        window.seed(headHeight = 1000, chainTimeSpan = 5.minutes, at = T0 + 30.seconds)

        assertEquals(measured(1, 15), window.produced(T0 + 30.seconds))
    }

    @Test
    fun `a restart drops what the previous connection saw`() {
        val window = peopleWindow()
        window.record(height = 100, at = T0)
        window.record(height = 115, at = T0 + 30.seconds)
        window.restart(T0 + 30.seconds)

        assertNull(window.produced(T0 + 30.seconds))
        assertEquals(measured(0, 3), window.produced(T0 + 36.seconds))
    }

    private fun peopleWindow() = BlockProductionWindow(blockTime = 2.seconds, expectedBlocks = 15)

    private fun measured(blocks: Int, expected: Int) = BlockProductionWindow.Measured(blocks, expected)

    private companion object {
        val T0: Instant = Instant.fromEpochMilliseconds(1_000_000)
    }
}
