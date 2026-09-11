package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

class ChainHealthIndicatorMapperTest {
    @Test
    fun `disconnected wins over everything`() {
        val health = health(ChainConnectionPresentation.Disconnected, blocks(5, 5), pending(100))

        assertEquals(ChainHealthIndicator.Disconnected, health.toIndicator())
    }

    @Test
    fun `connecting wins over an outage`() {
        val health = health(ChainConnectionPresentation.Connecting, blocks(0, 5))

        assertEquals(ChainHealthIndicator.Connecting, health.toIndicator())
    }

    @Test
    fun `a node that stopped answering is dead even while the socket is connected`() {
        assertEquals(ChainHealthIndicator.Disconnected, connected(blocks(5, 5), pending(0)).toIndicator())
    }

    @Test
    fun `no readings is healthy`() {
        assertEquals(ChainHealthIndicator.Healthy, connected().toIndicator())
    }

    @Test
    fun `five sixths of the expected blocks is the outage line`() {
        assertEquals(ChainHealthIndicator.Healthy, connected(blocks(5, 5)).toIndicator())
        assertEquals(ChainHealthIndicator.Healthy, connected(blocks(5, 6)).toIndicator())
        assertEquals(ChainHealthIndicator.Outage(4, 5), connected(blocks(4, 5)).toIndicator())
        assertEquals(ChainHealthIndicator.Outage(0, 5), connected(blocks(0, 5)).toIndicator())
    }

    @Test
    fun `an outage wins over a slow connection`() {
        assertEquals(ChainHealthIndicator.Outage(3, 5), connected(blocks(3, 5), pending(10)).toIndicator())
    }

    @Test
    fun `connection speed is graded by the worst of pending and response`() {
        assertEquals(ChainHealthIndicator.Healthy, connected(pending(90), response(100)).toIndicator())
        assertEquals(speed(Speed.Good), connected(pending(100), response(89)).toIndicator())
        assertEquals(speed(Speed.Good), connected(pending(70)).toIndicator())
        assertEquals(speed(Speed.Fair), connected(pending(69)).toIndicator())
        assertEquals(speed(Speed.Fair), connected(pending(40)).toIndicator())
        assertEquals(speed(Speed.Low), connected(pending(39)).toIndicator())
        assertEquals(speed(Speed.Low), connected(response(0)).toIndicator())
    }

    @Test
    fun `an outage wins over any speed band`() {
        assertEquals(ChainHealthIndicator.Outage(3, 5), connected(blocks(3, 5), pending(75)).toIndicator())
    }

    @Test
    fun `block latency and finality gap do not colour the indicator`() {
        val health = connected(
            blocks(5, 5),
            ChainMetricReading.BlockLatency(latency = 60.seconds, target = 6.seconds, score = ChainHealthScore.Zero),
            ChainMetricReading.FinalityGap(gapBlocks = 100, targetBlocks = 6, score = ChainHealthScore.Zero),
        )

        assertEquals(ChainHealthIndicator.Healthy, health.toIndicator())
    }

    private fun speed(speed: Speed) = ChainHealthIndicator.ConnectionSpeed(speed)

    private fun connected(vararg readings: ChainMetricReading): ChainHealth =
        health(ChainConnectionPresentation.Connected, *readings)

    private fun health(connection: ChainConnectionPresentation, vararg readings: ChainMetricReading): ChainHealth =
        ChainHealth(
            chainId = "people",
            chainName = "People",
            connection = connection,
            expectedBlockTime = 6.seconds,
            readings = readings.toList(),
        )

    private fun blocks(recent: Int, expected: Int): ChainMetricReading = ChainMetricReading.BlockProduction(
        recentBlocks = recent,
        expectedBlocks = expected,
        requiredBlocks = ceil(expected * 5.0 / 6.0).toInt(),
        score = ChainHealthScore.coerced(recent * ChainHealthScore.MAX_VALUE / expected),
    )

    private fun pending(score: Int): ChainMetricReading = ChainMetricReading.PendingRequestLatency(
        latency = 1.seconds,
        target = 1.seconds,
        score = ChainHealthScore.coerced(score),
    )

    private fun response(score: Int): ChainMetricReading = ChainMetricReading.ResponseLatency(
        latency = 1.seconds,
        target = 1.seconds,
        score = ChainHealthScore.coerced(score),
    )
}
