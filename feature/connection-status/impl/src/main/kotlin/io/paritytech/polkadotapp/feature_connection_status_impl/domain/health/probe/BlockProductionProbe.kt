package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.data.BlockProductionAnchor
import io.paritytech.polkadotapp.feature_connection_status_impl.data.BlockProductionAnchorDataSource
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockProductionWindow
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.ChainHealthThresholds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class BlockProductionProbe @Inject constructor(
    private val timeProvider: TimeProvider,
    private val anchorDataSource: BlockProductionAnchorDataSource,
) : ChainHealthProbe {
    override fun observe(context: ChainMetricContext): Flow<ChainMetricReading> = flow {
        val blockTime = context.expectedBlockTime
        val expectedBlocks = (ChainHealthThresholds.MIN_BLOCK_PRODUCTION_WINDOW / blockTime)
            .roundToInt()
            .coerceAtLeast(ChainHealthThresholds.MIN_EXPECTED_BLOCKS)
        val production = BlockProductionWindow(blockTime = blockTime, expectedBlocks = expectedBlocks)

        val connects = context.connection
            .map { it == ChainConnectionPresentation.Connected }
            .distinctUntilChanged()
            .filter { connected -> connected }

        val blocks = context.bestBlockNumber.distinctUntilChanged().map(Event::Block)
        val ticks = context.ticks.map { Event.Tick }
        // The restart must precede the seed it belongs to, and an answer a newer connect has already
        // superseded must be dropped.
        val connectEvents = connects.transformLatest { _ ->
            emit(Event.Reconnected)

            anchorDataSource.fetch(context.chainId, expectedBlocks)
                .logFailure(ANCHOR_FAILURE)
                .getOrNull()
                ?.let { anchor -> emit(Event.Anchored(anchor)) }
        }

        emitAll(
            merge(blocks, connectEvents, ticks).map { event ->
                val now = timeProvider.now()
                when (event) {
                    is Event.Block -> production.record(event.height, now)
                    Event.Reconnected -> production.restart(now)
                    is Event.Anchored -> production.seed(event.anchor.headHeight, event.anchor.chainTimeSpan, now)
                    Event.Tick -> Unit
                }

                val measured = production.produced(now) ?: fullWindow(expectedBlocks)

                ChainMetricReading.BlockProduction(recentBlocks = measured.blocks, expectedBlocks = measured.expected)
            }.distinctUntilChanged(),
        )
    }

    private sealed interface Event {
        data class Block(val height: Int) : Event
        data object Reconnected : Event
        data class Anchored(val anchor: BlockProductionAnchor) : Event
        data object Tick : Event
    }

    private companion object {
        const val ANCHOR_FAILURE = "Failed to anchor block production"

        fun fullWindow(expectedBlocks: Int) = BlockProductionWindow.Measured(expectedBlocks, expectedBlocks)
    }
}
