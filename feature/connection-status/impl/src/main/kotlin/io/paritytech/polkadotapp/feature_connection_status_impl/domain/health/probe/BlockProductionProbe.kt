package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.BlockArrivalWindow
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BlockProductionProbe @Inject constructor(
    private val timeProvider: TimeProvider,
) : ChainHealthProbe {
    override fun observe(context: ChainMetricContext): Flow<ChainMetricReading> = flow {
        val window = ChainHealthThresholds.BLOCK_PRODUCTION_WINDOW
        val blockTime = context.expectedBlockTime.coerceAtLeast(1.milliseconds)
        val expectedBlocks = (window / blockTime).toInt().coerceAtLeast(1)
        val requiredBlocks = ceil(expectedBlocks * ChainHealthThresholds.BLOCK_PRODUCTION_REQUIRED_RATIO).toInt()
        var arrivals = BlockArrivalWindow(window + blockTime)
        var observingSince: Instant = timeProvider.now()

        val blocks = context.bestBlockNumber.distinctUntilChanged().map { Event.Block }
        val reconnects = context.connection
            .map { it == ChainConnectionPresentation.Connected }
            .distinctUntilChanged()
            .filter { connected -> connected }
            .map { Event.Reconnected }
        val ticks = ticker(ChainHealthThresholds.LIVENESS_TICK)

        emitAll(
            merge(blocks, reconnects, ticks).map { event ->
                val now = timeProvider.now()
                when (event) {
                    Event.Block -> arrivals.recordArrival(now)
                    Event.Reconnected -> {
                        arrivals = BlockArrivalWindow(window + blockTime)
                        observingSince = now
                    }
                    Event.Tick -> Unit
                }

                val warmingUp = now - observingSince < window
                val recentBlocks = if (warmingUp) expectedBlocks else arrivals.pruneAndCount(now).coerceAtMost(expectedBlocks)

                ChainMetricReading.BlockProduction(
                    recentBlocks = recentBlocks,
                    expectedBlocks = expectedBlocks,
                    requiredBlocks = requiredBlocks,
                    score = ChainHealthScore.coerced(recentBlocks * ChainHealthScore.MAX_VALUE / expectedBlocks),
                )
            }.distinctUntilChanged(),
        )
    }

    private fun ticker(period: Duration): Flow<Event> = flow {
        while (true) {
            emit(Event.Tick)
            delay(period)
        }
    }

    private sealed interface Event {
        data object Block : Event
        data object Reconnected : Event
        data object Tick : Event
    }
}
