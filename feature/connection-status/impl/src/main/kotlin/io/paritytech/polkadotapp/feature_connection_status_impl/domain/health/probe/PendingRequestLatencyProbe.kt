package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.probe

import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.PendingRequestTracker
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

// Requests carry no timestamp of their own, so the tracker identifies them referentially.
@OptIn(ExperimentalTime::class)
class PendingRequestLatencyProbe @Inject constructor(
    private val timeProvider: TimeProvider,
) : ChainHealthProbe {
    override fun observe(context: ChainMetricContext): Flow<ChainMetricReading> {
        val tracker = PendingRequestTracker()
        val threshold = context.expectedBlockTime * ChainHealthThresholds.NODE_SILENT_BLOCK_TIMES

        return combine(context.pendingRequests, context.ticks) { pending, _ ->
            val latency = tracker.update(pending, timeProvider.now().toEpochMilliseconds()).milliseconds

            ChainMetricReading.PendingRequestLatency(
                latency = latency,
                score = if (latency > threshold) ChainHealthScore.Zero else ChainHealthScore.Perfect,
            )
        }.distinctUntilChangedBy { it.score }
    }
}
