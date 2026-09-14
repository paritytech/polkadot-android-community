package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockProductionAnchor
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockProductionWindow
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class BlockProductionProbe @Inject constructor(
    private val timeProvider: TimeProvider,
) : ChainHealthProbe {
    override fun observe(context: ChainMetricContext): Flow<ChainMetricReading> = flow {
        val blockTime = context.expectedBlockTime.coerceAtLeast(MIN_BLOCK_TIME)
        val window = maxOf(
            ChainHealthThresholds.BLOCK_PRODUCTION_MIN_WINDOW,
            blockTime * ChainHealthThresholds.BLOCK_PRODUCTION_MIN_SLOTS,
        )
        val expectedBlocks = (window / blockTime).toInt().coerceAtLeast(MIN_EXPECTED_BLOCKS)
        val requiredBlocks = ceil(expectedBlocks * ChainHealthThresholds.BLOCK_PRODUCTION_REQUIRED_RATIO).toInt()
        val blocks = BlockProductionWindow(window)

        val connects = context.connection
            .map { it == ChainConnectionPresentation.Connected }
            .distinctUntilChanged()
            .filter { connected -> connected }

        val heads = context.bestBlockNumber.distinctUntilChanged().map(Event::Head)
        val reconnects = connects.map { Event.Reconnected }
        // mapLatest drops a fetch the next connect has already superseded, so a slow probe from a
        // dead connection can never seed the window of the one that replaced it.
        val anchors = connects
            .mapLatest { anchorOf(context, expectedBlocks) }
            .map(Event::Anchored)
        val ticks = context.ticks.map { Event.Tick }

        emitAll(
            merge(heads, reconnects, anchors, ticks).map { event ->
                val now = timeProvider.now()
                when (event) {
                    is Event.Head -> blocks.record(height = event.height, at = now)
                    Event.Reconnected -> blocks.clear()
                    is Event.Anchored -> event.anchor?.let { blocks.seed(it, window, now) }
                    Event.Tick -> Unit
                }

                // Null means the window does not reach back far enough to measure anything yet, which
                // is not the same as a chain that produced nothing; report it as producing.
                val recentBlocks = blocks.blocksProduced(now)?.coerceAtMost(expectedBlocks) ?: expectedBlocks

                ChainMetricReading.BlockProduction(
                    recentBlocks = recentBlocks,
                    expectedBlocks = expectedBlocks,
                    requiredBlocks = requiredBlocks,
                    lastBlockAt = blocks.lastArrival(),
                    score = ChainHealthScore.coerced(recentBlocks * ChainHealthScore.MAX_VALUE / expectedBlocks),
                )
            }.distinctUntilChanged(),
        )
    }

    private suspend fun anchorOf(context: ChainMetricContext, blocks: Int): BlockProductionAnchor? =
        withTimeoutOrNull(ChainHealthThresholds.BLOCK_PRODUCTION_ANCHOR_TIMEOUT) {
            runCatching { context.productionAnchor(blocks) }.getOrNull()
        }

    /**
     * Places the anchor's measurement on the window: if the chain took longer than a window to
     * produce those blocks, only the matching fraction of them belongs inside it.
     */
    private fun BlockProductionWindow.seed(anchor: BlockProductionAnchor, window: Duration, now: kotlin.time.Instant) {
        val produced = if (anchor.span <= window) {
            anchor.blocks
        } else {
            floor(anchor.blocks * (window / anchor.span)).toInt()
        }

        seed(headHeight = anchor.headHeight, producedInWindow = produced.coerceIn(0, anchor.headHeight), at = now)
    }

    private sealed interface Event {
        data class Head(val height: Int) : Event
        data class Anchored(val anchor: BlockProductionAnchor?) : Event
        data object Reconnected : Event
        data object Tick : Event
    }

    private companion object {
        val MIN_BLOCK_TIME: Duration = 1.milliseconds
        const val MIN_EXPECTED_BLOCKS = 1
    }
}
