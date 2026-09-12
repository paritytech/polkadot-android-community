package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed

// Bands for the worst of the pending-request and response scores.
private const val CONNECTION_HIGH_FROM = 90
private const val CONNECTION_GOOD_FROM = 70
private const val CONNECTION_FAIR_FROM = 40

internal fun ChainHealth.toIndicator(): ChainHealthIndicator = when (connection) {
    ChainConnectionPresentation.Disconnected -> ChainHealthIndicator.Disconnected
    ChainConnectionPresentation.Connecting -> ChainHealthIndicator.Connecting
    ChainConnectionPresentation.Connected -> when {
        nodeUnresponsive() -> ChainHealthIndicator.Disconnected
        notProducingBlocks() -> ChainHealthIndicator.Outage
        else -> connectionSpeed() ?: ChainHealthIndicator.Healthy
    }
}

private fun ChainHealth.nodeUnresponsive(): Boolean = readings
    .filterIsInstance<ChainMetricReading.PendingRequestLatency>()
    .any { it.score == ChainHealthScore.Zero }

private fun ChainHealth.notProducingBlocks(): Boolean = readings
    .filterIsInstance<ChainMetricReading.BlockProduction>()
    .any { it.recentBlocks < it.requiredBlocks }

private fun ChainHealth.connectionSpeed(): ChainHealthIndicator.ConnectionSpeed? {
    val worstScore = readings.filter { it.isConnectionSpeed() }.minOfOrNull { it.score.value } ?: return null
    val speed = when {
        worstScore >= CONNECTION_HIGH_FROM -> return null
        worstScore >= CONNECTION_GOOD_FROM -> Speed.Good
        worstScore >= CONNECTION_FAIR_FROM -> Speed.Fair
        else -> Speed.Low
    }
    return ChainHealthIndicator.ConnectionSpeed(speed)
}

private fun ChainMetricReading.isConnectionSpeed(): Boolean = when (this) {
    is ChainMetricReading.PendingRequestLatency, is ChainMetricReading.ResponseLatency -> true
    is ChainMetricReading.BlockLatency, is ChainMetricReading.BlockProduction, is ChainMetricReading.FinalityGap -> false
}
