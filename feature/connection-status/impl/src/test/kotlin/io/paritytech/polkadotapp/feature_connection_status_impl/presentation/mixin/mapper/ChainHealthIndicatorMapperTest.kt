package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Band
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ChainHealthIndicatorMapperTest {
    @Test
    fun `no internet is told apart from a broken chain`() {
        assertEquals(ChainHealthIndicator.NoInternet, health(ChainConnectionPresentation.NoInternet).toIndicator())
        assertEquals(ChainHealthIndicator.Broken, health(ChainConnectionPresentation.Disconnected).toIndicator())
    }

    @Test
    fun `a chain that cannot be reached is not judged on its production`() {
        assertEquals(
            ChainHealthIndicator.Connecting,
            health(ChainConnectionPresentation.Connecting, blocks(0, 15)).toIndicator(),
        )
        assertEquals(
            ChainHealthIndicator.NoInternet,
            health(ChainConnectionPresentation.NoInternet, blocks(15, 15)).toIndicator(),
        )
    }

    @Test
    fun `a node that stopped answering is broken even while the socket is connected`() {
        assertEquals(ChainHealthIndicator.Broken, connected(blocks(15, 15), silentNode()).toIndicator())
    }

    @Test
    fun `producing nothing at all draws the cross rather than an empty arc`() {
        assertEquals(ChainHealthIndicator.Outage, connected(blocks(0, 15)).toIndicator())
    }

    @Test
    fun `the five sixths mark closes the ring`() {
        assertEquals(Band.Full, bandOf(connected(blocks(15, 15))))
        assertEquals(Band.Full, bandOf(connected(blocks(13, 15))))
        assertEquals(Band.Neutral, bandOf(connected(blocks(12, 15))))

        assertEquals(Band.Full, bandOf(connected(blocks(9, 10))))
        assertEquals(Band.Neutral, bandOf(connected(blocks(8, 10))))
    }

    // Five sixths lands exactly on a block count only when the expected count is a multiple of six,
    // and floating point must not put it on the wrong side of its own threshold.
    @Test
    fun `a share of exactly five sixths still closes the ring`() {
        assertEquals(Band.Full, bandOf(connected(blocks(5, 6))))
        assertEquals(Band.Full, bandOf(connected(blocks(10, 12))))
        assertEquals(Band.Neutral, bandOf(connected(blocks(9, 12))))
    }

    @Test
    fun `colour changes on the quarters, with each band owning its own floor`() {
        assertEquals(Band.Neutral, bandOf(connected(blocks(9, 15))))
        assertEquals(Band.Neutral, bandOf(connected(blocks(8, 15))))
        assertEquals(Band.Warning, bandOf(connected(blocks(7, 15))))
        assertEquals(Band.Warning, bandOf(connected(blocks(4, 15))))
        assertEquals(Band.Error, bandOf(connected(blocks(3, 15))))
        assertEquals(Band.Error, bandOf(connected(blocks(1, 15))))
    }

    @Test
    fun `exactly half the expected blocks is white, the floor of its band`() {
        assertEquals(Band.Neutral, bandOf(connected(blocks(5, 10))))
        assertEquals(Band.Warning, bandOf(connected(blocks(4, 10))))
    }

    @Test
    fun `a reading claiming more than it owed still draws inside the ring`() {
        val indicator = producing(connected(blocks(15, 4)))

        assertTrue("share must stay within the ring: ${indicator.share}", indicator.share <= 1f)
        assertEquals(Band.Full, indicator.band)
    }

    @Test
    fun `the arc is the share itself`() {
        assertShare(1f, connected(blocks(15, 15)))
        assertShare(0.6f, connected(blocks(9, 15)))
        assertShare(0.4f, connected(blocks(6, 15)))
        assertShare(0.2f, connected(blocks(3, 15)))
    }

    @Test
    fun `the block interval is the expected one stretched by the share`() {
        assertEquals(12.seconds, producing(connected(blocks(5, 10))).blockInterval)
        assertEquals(6.seconds, producing(connected(blocks(10, 10))).blockInterval)
    }

    private fun assertShare(expected: Float, health: ChainHealth) {
        val actual = producing(health).share

        assertTrue("expected a share of $expected but got $actual", abs(expected - actual) < SHARE_TOLERANCE)
    }

    private fun bandOf(health: ChainHealth): Band = producing(health).band

    private fun producing(health: ChainHealth): ChainHealthIndicator.Producing =
        health.toIndicator() as? ChainHealthIndicator.Producing ?: throw AssertionError("${health.readings} is not producing")

    private fun connected(vararg readings: ChainMetricReading): ChainHealth =
        health(ChainConnectionPresentation.Connected, *readings)

    private fun health(connection: ChainConnectionPresentation, vararg readings: ChainMetricReading): ChainHealth =
        ChainHealth(
            chainId = "people",
            chainName = "People",
            connection = connection,
            expectedBlockTime = BLOCK_TIME,
            readings = readings.toList(),
        )

    private fun blocks(recent: Int, expected: Int): ChainMetricReading =
        ChainMetricReading.BlockProduction(recentBlocks = recent, expectedBlocks = expected)

    private fun silentNode(): ChainMetricReading = ChainMetricReading.PendingRequestLatency(
        latency = 1.seconds,
        score = ChainHealthScore.Zero,
    )

    private companion object {
        const val SHARE_TOLERANCE = 0.011f
        val BLOCK_TIME: Duration = 6.seconds
    }
}
