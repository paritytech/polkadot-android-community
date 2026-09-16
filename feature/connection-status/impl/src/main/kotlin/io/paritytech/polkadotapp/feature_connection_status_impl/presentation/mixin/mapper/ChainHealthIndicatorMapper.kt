package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import kotlin.time.Duration

internal fun ChainHealth.toIndicator(): ChainHealthIndicator = when (connection) {
    ChainConnectionPresentation.NoInternet -> ChainHealthIndicator.NoInternet
    ChainConnectionPresentation.Disconnected -> ChainHealthIndicator.Broken
    ChainConnectionPresentation.Connecting -> ChainHealthIndicator.Connecting
    ChainConnectionPresentation.Connected -> when {
        nodeSilent() -> ChainHealthIndicator.Broken
        else -> blockProduction()?.toIndicator(expectedBlockTime) ?: ChainHealthIndicator.Connecting
    }
}

private fun ChainMetricReading.BlockProduction.toIndicator(blockTime: Duration): ChainHealthIndicator =
    ChainHealthIndicator.producing(recentBlocks.toFloat() / expectedBlocks, blockTime)

private fun ChainHealth.blockProduction(): ChainMetricReading.BlockProduction? =
    readings.filterIsInstance<ChainMetricReading.BlockProduction>().firstOrNull()

private fun ChainHealth.nodeSilent(): Boolean = readings
    .filterIsInstance<ChainMetricReading.PendingRequestLatency>()
    .any { it.score == ChainHealthScore.Zero }
