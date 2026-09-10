package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Tone
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class ChainHealthIndicatorMapperTest {
    @Test
    fun `disconnected wins over a perfect score`() {
        val health = health(ChainConnectionPresentation.Disconnected, socketSide(100))

        assertEquals(ChainHealthIndicator.Disconnected, health.toIndicator())
    }

    @Test
    fun `connecting wins over a perfect score`() {
        val health = health(ChainConnectionPresentation.Connecting, socketSide(100))

        assertEquals(ChainHealthIndicator.Connecting, health.toIndicator())
    }

    @Test
    fun `no readings is healthy`() {
        assertEquals(ChainHealthIndicator.Healthy, health(ChainConnectionPresentation.Connected).toIndicator())
    }

    @Test
    fun `score at the healthy threshold is healthy whatever the metric`() {
        assertEquals(ChainHealthIndicator.Healthy, connected(chainSide(90)).toIndicator())
        assertEquals(ChainHealthIndicator.Healthy, connected(socketSide(90)).toIndicator())
    }

    @Test
    fun `a degraded chain-side reading is always an error arc`() {
        assertDegraded(Tone.Error, 0.89f, connected(chainSide(89)).toIndicator())
        assertDegraded(Tone.Error, 0.5f, connected(chainSide(50), socketSide(60)).toIndicator())
    }

    @Test
    fun `a degraded chain-side reading wins over a slower socket-side one`() {
        assertDegraded(Tone.Error, 0.5f, connected(chainSide(60), socketSide(50)).toIndicator())
        assertDegraded(Tone.Error, 0.4f, connected(chainSide(40), socketSide(40)).toIndicator())
    }

    @Test
    fun `a socket-side worst reading is graded by tier`() {
        assertDegraded(Tone.Neutral, 0.7f, connected(socketSide(70)).toIndicator())
        assertDegraded(Tone.Warning, 0.4f, connected(socketSide(40)).toIndicator())
        assertDegraded(Tone.Error, 0.39f, connected(socketSide(39)).toIndicator())
    }

    @Test
    fun `a healthy chain-side reading leaves the socket ladder in charge`() {
        assertDegraded(Tone.Warning, 0.5f, connected(chainSide(95), socketSide(50)).toIndicator())
    }

    private fun assertDegraded(tone: Tone, fraction: Float, actual: ChainHealthIndicator) {
        val degraded = actual as ChainHealthIndicator.Degraded
        assertEquals(tone, degraded.tone)
        assertEquals(fraction, degraded.fraction, FRACTION_TOLERANCE)
    }

    private fun connected(vararg readings: ChainMetricReading): ChainHealth =
        health(ChainConnectionPresentation.Connected, *readings)

    private fun health(connection: ChainConnectionPresentation, vararg readings: ChainMetricReading): ChainHealth =
        ChainHealth(
            chainId = "people",
            chainName = "People",
            connection = connection,
            score = readings.minOfOrNull { it.score } ?: ChainHealthScore.Perfect,
            readings = readings.toList(),
        )

    private fun chainSide(score: Int): ChainMetricReading = ChainMetricReading.BlockLatency(
        latency = 6.seconds,
        target = 6.seconds,
        score = ChainHealthScore.coerced(score),
    )

    private fun socketSide(score: Int): ChainMetricReading = ChainMetricReading.ResponseLatency(
        latency = 1.seconds,
        target = 1.seconds,
        score = ChainHealthScore.coerced(score),
    )

    private companion object {
        const val FRACTION_TOLERANCE = 0.0001f
    }
}
