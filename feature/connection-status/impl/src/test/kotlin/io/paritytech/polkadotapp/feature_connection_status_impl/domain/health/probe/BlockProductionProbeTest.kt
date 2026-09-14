package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockProductionAnchor
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BlockProductionProbeTest {
    @Test
    fun `the window is at least thirty seconds and at least ten block periods`() = runTest {
        val fast = collectReadings(blockTime = 2.seconds)
        runCurrent()
        // 30s / 2s: the floor window already holds more than ten slots.
        assertEquals(15, fast.last().expectedBlocks)
        assertEquals(13, fast.last().requiredBlocks)

        val slow = collectReadings(blockTime = 6.seconds)
        runCurrent()
        // 60s / 6s: ten slots rather than the five a flat window would give.
        assertEquals(10, slow.last().expectedBlocks)
        assertEquals(9, slow.last().requiredBlocks)
    }

    @Test
    fun `the slower chain can miss a block without reading as an outage`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(60_000)
        heads.emit(109)
        runCurrent()

        assertEquals(9, readings.last().recentBlocks)
        assertEquals(9, readings.last().requiredBlocks)
    }

    @Test
    fun `production is the height difference, not the number of heads delivered`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(60_000)
        // Eight of the ten heads never arrived; the chain still produced ten blocks.
        heads.emit(110)
        runCurrent()

        assertEquals(10, readings.last().recentBlocks)
        assertEquals(100, readings.last().score.value)
    }

    @Test
    fun `an unmeasurable window reports the chain as producing`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(30_000); runCurrent()

        assertEquals(10, readings.last().recentBlocks)
    }

    @Test
    fun `a stall decays to zero once the window moves past the last head`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(60_000)
        heads.emit(110)
        runCurrent()
        assertEquals(10, readings.last().recentBlocks)

        advanceTimeBy(70_000); runCurrent()
        assertEquals(0, readings.last().recentBlocks)
        assertEquals(0, readings.last().score.value)
    }

    @Test
    fun `more blocks than expected are capped`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(60_000)
        heads.emit(200)
        runCurrent()

        assertEquals(10, readings.last().recentBlocks)
    }

    @Test
    fun `an anchor flags a stalled chain without waiting out a window`() = runTest {
        // Ten blocks took ten windows of chain time, so only one of them belongs inside one window.
        val readings = collectReadings(
            blockTime = 6.seconds,
            anchor = { blocks -> BlockProductionAnchor(headHeight = 1_000, blocks = blocks, span = 600.seconds) },
        )
        runCurrent()

        assertEquals(1, readings.last().recentBlocks)
    }

    @Test
    fun `an anchor on a chain running to time reports it as producing`() = runTest {
        val readings = collectReadings(
            blockTime = 6.seconds,
            anchor = { blocks -> BlockProductionAnchor(headHeight = 1_000, blocks = blocks, span = 60.seconds) },
        )
        runCurrent()

        assertEquals(10, readings.last().recentBlocks)
    }

    @Test
    fun `a failed anchor leaves the window to fill from observation`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds, anchor = { null })
        runCurrent()

        assertEquals(10, readings.last().recentBlocks)
        assertNull(readings.last().lastBlockAt)
    }

    @Test
    fun `a reconnect drops the heights the previous connection observed`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        heads.emit(100)
        advanceTimeBy(60_000)
        heads.emit(101)
        runCurrent()
        assertEquals(1, readings.last().recentBlocks)

        connection.value = ChainConnectionPresentation.Connecting
        connection.value = ChainConnectionPresentation.Connected
        runCurrent()

        assertEquals(10, readings.last().recentBlocks)
    }

    @Test
    fun `the outage line stays on the configured block time`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        repeat(4) { index ->
            heads.emit(index)
            advanceTimeBy(2_000); runCurrent()
        }

        // Ten slots of the configured 6s, not of the 2s actually seen.
        assertEquals(10, readings.last().expectedBlocks)
    }

    @Test
    fun `reports when the last head landed`() = runTest {
        val readings = collectReadings(blockTime = 6.seconds)
        advanceTimeBy(10_000); runCurrent()
        heads.emit(1)
        runCurrent()
        val landed = testScheduler.currentTime

        advanceTimeBy(5_000); runCurrent()

        assertEquals(Instant.fromEpochMilliseconds(landed), readings.last().lastBlockAt)
    }

    private lateinit var heads: MutableSharedFlow<Int>
    private lateinit var connection: MutableStateFlow<ChainConnectionPresentation>

    private fun sampleTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(ChainHealthThresholds.SAMPLE_TICK)
        }
    }

    private fun TestScope.collectReadings(
        blockTime: Duration,
        anchor: suspend (Int) -> BlockProductionAnchor? = { null },
    ): List<ChainMetricReading.BlockProduction> {
        heads = MutableSharedFlow(extraBufferCapacity = 64)
        connection = MutableStateFlow(ChainConnectionPresentation.Connected)
        val probe = BlockProductionProbe(FakeTimeProvider { testScheduler.currentTime })
        val context = ChainMetricContext(
            bestBlockNumber = heads,
            expectedBlockTime = blockTime,
            pendingRequests = emptyFlow(),
            connection = connection,
            ticks = sampleTicks(),
            productionAnchor = anchor,
        )
        val results = mutableListOf<ChainMetricReading.BlockProduction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            probe.observe(context).collect { results += it as ChainMetricReading.BlockProduction }
        }
        return results
    }
}
