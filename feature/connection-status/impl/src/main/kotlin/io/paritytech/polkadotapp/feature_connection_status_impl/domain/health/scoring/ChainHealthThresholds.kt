package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object ChainHealthThresholds {
    // Re-evaluation cadence, so the share decays during a stall instead of holding its last value.
    val SAMPLE_TICK: Duration = 1.seconds

    val MIN_BLOCK_PRODUCTION_WINDOW: Duration = 30.seconds

    // At the shortest window a six-second chain expects five blocks, making every band one block wide.
    const val MIN_EXPECTED_BLOCKS = 10

    // Nothing else times out an unanswered request: not the request, not the socket read, not a missing pong.
    const val NODE_SILENT_BLOCK_TIMES = 3

    val ANCHOR_TIMEOUT: Duration = 15.seconds
}
