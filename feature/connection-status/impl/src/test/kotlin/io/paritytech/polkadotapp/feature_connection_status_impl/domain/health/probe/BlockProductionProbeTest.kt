package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.data.BlockProductionAnchor
import io.paritytech.polkadotapp.feature_connection_status_impl.data.BlockProductionAnchorDataSource
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.ChainHealthThresholds
import io.paritytech.polkadotapp.test_shared.FakeTimeProvider
import io.paritytech.polkadotapp.test_shared.any
import io.paritytech.polkadotapp.test_shared.anyInt
import io.paritytech.polkadotapp.test_shared.whenever
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BlockProductionProbeTest {
    @Test
    fun `a two second chain expects a block for every two seconds of the window`() = runTest {
        withNoAnchor()

        val readings = collectReadings(blockTime = 2.seconds)
        runCurrent()

        assertEquals(15, readings.last().expectedBlocks)
    }

    // A flat window would leave the six-second chain expecting five blocks, one per band.
    @Test
    fun `a six second chain widens its window until ten blocks fit`() = runTest {
        withNoAnchor()

        val readings = collectReadings(blockTime = 6.seconds)
        runCurrent()

        assertEquals(10, readings.last().expectedBlocks)
    }

    @Test
    fun `an unanswered anchor is optimistic only for the first few block times`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)
        runCurrent()

        assertEquals(15, readings.last().recentBlocks)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(15, readings.last().recentBlocks)

        advanceTimeBy(2_000); runCurrent()
        assertEquals(0, readings.last().recentBlocks)
    }

    @Test
    fun `a chain keeping up is judged against what it owed so far`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)

        listOf(100, 101, 102, 103).forEach { height ->
            advanceTimeBy(2_000); heads.emit(height); runCurrent()
        }

        // Four heads two seconds apart: three blocks of growth, and three is also what it owed.
        assertEquals(3, readings.last().recentBlocks)
        assertEquals(3, readings.last().expectedBlocks)

        // Every tick in between must read as keeping up too, not only the ones landing on a block.
        val shortfalls = readings.filter { it.recentBlocks < it.expectedBlocks }
        assertTrue("a chain keeping up must never report a shortfall: $shortfalls", shortfalls.isEmpty())
    }

    // A connected chain that produces nothing and whose anchor never answers must still reach the cross,
    // on the first connection and on every one after it.
    @Test
    fun `a chain that never delivers a block reaches zero`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)

        advanceTimeBy(31_000); runCurrent()
        assertEquals(0, readings.last().recentBlocks)

        connection.value = ChainConnectionPresentation.Connecting
        runCurrent()
        connection.value = ChainConnectionPresentation.Connected
        advanceTimeBy(31_000); runCurrent()
        assertEquals(0, readings.last().recentBlocks)
    }

    @Test
    fun `counts the growth in height rather than the arrivals it was told about`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)

        heads.emit(100)
        advanceTimeBy(30_000)
        // One notification for nine blocks: the chain produced them whether or not we heard about each.
        heads.emit(109)
        runCurrent()

        assertEquals(9, readings.last().recentBlocks)
    }

    @Test
    fun `a stall decays to zero on its own`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)

        heads.emit(100)
        advanceTimeBy(30_000)
        heads.emit(109)
        runCurrent()
        assertEquals(9, readings.last().recentBlocks)

        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, readings.last().recentBlocks)
    }

    @Test
    fun `a reconnect drops what the previous connection saw`() = runTest {
        withNoAnchor()
        val readings = collectReadings(blockTime = 2.seconds)

        heads.emit(100)
        advanceTimeBy(30_000)
        heads.emit(109)
        runCurrent()
        assertEquals(9, readings.last().recentBlocks)

        connection.value = ChainConnectionPresentation.Connecting
        runCurrent()
        connection.value = ChainConnectionPresentation.Connected
        runCurrent()

        assertEquals(15, readings.last().recentBlocks)
    }

    @Test
    fun `an anchor reports a slow chain at once instead of after a full window`() = runTest {
        whenever(anchorDataSource.fetch(any(), anyInt()))
            .thenReturn(Result.success(BlockProductionAnchor(headHeight = 1_000, chainTimeSpan = 5.minutes)))

        val readings = collectReadings(blockTime = 2.seconds)
        runCurrent()

        assertEquals(1, readings.last().recentBlocks)
    }

    // The restart a reconnect performs and the seed its anchor produces must stay in that order: the
    // other way round the anchor is wiped and the chain reads healthy for a whole window.
    @Test
    fun `a reconnect re-anchors instead of being cleared back to optimistic`() = runTest {
        whenever(anchorDataSource.fetch(any(), anyInt()))
            .thenReturn(Result.success(BlockProductionAnchor(headHeight = 1_000, chainTimeSpan = 5.minutes)))

        val readings = collectReadings(blockTime = 2.seconds)
        runCurrent()
        assertEquals(1, readings.last().recentBlocks)

        connection.value = ChainConnectionPresentation.Connecting
        runCurrent()
        connection.value = ChainConnectionPresentation.Connected
        runCurrent()

        assertEquals(1, readings.last().recentBlocks)
    }

    private val anchorDataSource: BlockProductionAnchorDataSource = mock()

    private lateinit var heads: MutableSharedFlow<Int>
    private lateinit var connection: MutableStateFlow<ChainConnectionPresentation>

    private suspend fun withNoAnchor() {
        whenever(anchorDataSource.fetch(any(), anyInt())).thenReturn(Result.failure(IllegalStateException()))
    }

    private fun sampleTicks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(ChainHealthThresholds.SAMPLE_TICK)
        }
    }

    private fun TestScope.collectReadings(blockTime: Duration): List<ChainMetricReading.BlockProduction> {
        heads = MutableSharedFlow(extraBufferCapacity = 64)
        connection = MutableStateFlow(ChainConnectionPresentation.Connected)
        val probe = BlockProductionProbe(FakeTimeProvider { testScheduler.currentTime }, anchorDataSource)
        val context = ChainMetricContext(
            chainId = "people",
            bestBlockNumber = heads,
            expectedBlockTime = blockTime,
            pendingRequests = emptyFlow(),
            connection = connection,
            ticks = sampleTicks(),
        )
        val results = mutableListOf<ChainMetricReading.BlockProduction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            probe.observe(context).collect { results += it as ChainMetricReading.BlockProduction }
        }
        return results
    }
}
