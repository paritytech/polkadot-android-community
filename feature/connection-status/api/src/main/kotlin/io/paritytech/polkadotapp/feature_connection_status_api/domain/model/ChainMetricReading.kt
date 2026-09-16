package io.paritytech.polkadotapp.feature_connection_status_api.domain.model

import kotlin.time.Duration

/**
 * One metric's contribution for a chain. The metric type and its value are one sealed field — adding a
 * metric adds a variant here, a probe that emits it, and a UI branch that formats it. Formatting stays
 * in the presentation layer.
 */
sealed interface ChainMetricReading {
    /**
     * How much of the chain's expected output actually arrived over the interval measured.
     * [recentBlocks] counts the growth in chain height, not the arrivals we were notified of, so
     * notifications lost on the way are not blamed on the chain. [expectedBlocks] is what the chain
     * owed over that same interval, derived from its configured block time, so the measurement cannot
     * drift towards whatever rate the chain currently runs at.
     */
    data class BlockProduction(
        val recentBlocks: Int,
        val expectedBlocks: Int,
    ) : ChainMetricReading

    /**
     * How long the oldest still-pending socket request has been waiting. [score] is binary: the node
     * either answered in time or counts as silent, since nothing else in the app times one out.
     */
    data class PendingRequestLatency(
        val latency: Duration,
        val score: ChainHealthScore,
    ) : ChainMetricReading
}
