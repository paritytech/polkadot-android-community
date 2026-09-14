package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BlockProductionWindowTest {
    @Test
    fun `measures production as the height difference across the window`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(0))
        window.record(height = 115, at = at(30))

        assertEquals(15, window.blocksProduced(at(30)))
    }

    @Test
    fun `a head the subscription never delivered still lies between two heights`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(0))
        // Thirteen heads in between were produced but never arrived.
        window.record(height = 115, at = at(30))

        assertEquals(15, window.blocksProduced(at(30)))
    }

    @Test
    fun `an unmeasurable span is not a stalled chain`() {
        val window = BlockProductionWindow(window = 30.seconds)

        assertNull(window.blocksProduced(at(10)))

        window.record(height = 100, at = at(10))

        assertNull(window.blocksProduced(at(20)))
    }

    @Test
    fun `a stall reads as nothing produced once the window has moved past the last head`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(0))
        window.record(height = 115, at = at(30))

        assertEquals(0, window.blocksProduced(at(70)))
    }

    @Test
    fun `the sample at the window start is kept as the height the difference is measured from`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(0))
        window.record(height = 110, at = at(20))
        window.record(height = 120, at = at(40))

        // Measured from the 20s sample, the newest at or before the window start.
        assertEquals(10, window.blocksProduced(at(50)))
    }

    @Test
    fun `a reorg below the anchor reads as nothing produced rather than underflowing`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 200, at = at(0))
        window.record(height = 150, at = at(30))

        assertEquals(0, window.blocksProduced(at(30)))
    }

    @Test
    fun `seeding makes production measurable at once`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.seed(headHeight = 1_000, producedInWindow = 4, at = at(10))

        assertEquals(4, window.blocksProduced(at(10)))
    }

    @Test
    fun `seeding is not an observation and claims no arrival`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(0))
        window.seed(headHeight = 1_000, producedInWindow = 4, at = at(10))

        assertNull(window.lastArrival())
    }

    @Test
    fun `remembers the last arrival after the window has moved past it`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(5))

        assertEquals(at(5), window.lastArrival())
        assertEquals(0, window.blocksProduced(at(90)))
        assertEquals(at(5), window.lastArrival())
    }

    @Test
    fun `a reconnect forgets the last arrival`() {
        val window = BlockProductionWindow(window = 30.seconds)
        window.record(height = 100, at = at(5))
        window.clear()

        assertNull(window.lastArrival())
    }

    private fun at(seconds: Int): Instant = Instant.fromEpochMilliseconds(seconds * 1_000L)
}
