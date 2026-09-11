package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed
import io.paritytech.polkadotapp.feature_connection_status_impl.domain.health.scoring.ChainHealthThresholds

fun ChainHealth.toIndicator(): ChainHealthIndicator = when (connection) {
    ChainConnectionPresentation.Disconnected -> ChainHealthIndicator.Disconnected
    ChainConnectionPresentation.Connecting -> ChainHealthIndicator.Connecting
    ChainConnectionPresentation.Connected -> when {
        nodeUnresponsive() -> ChainHealthIndicator.Disconnected
        else -> outage() ?: connectionSpeed() ?: ChainHealthIndicator.Healthy
    }
}

private fun ChainHealth.nodeUnresponsive(): Boolean = readings
    .filterIsInstance<ChainMetricReading.PendingRequestLatency>()
    .any { it.score == ChainHealthScore.Zero }

private fun ChainHealth.outage(): ChainHealthIndicator.Outage? {
    val production = readings.filterIsInstance<ChainMetricReading.BlockProduction>().firstOrNull() ?: return null
    if (production.recentBlocks >= production.requiredBlocks) return null
    return ChainHealthIndicator.Outage(production.recentBlocks, production.expectedBlocks)
}

private fun ChainHealth.connectionSpeed(): ChainHealthIndicator.ConnectionSpeed? {
    val worstScore = readings.filter { it.isConnectionSpeed() }.minOfOrNull { it.score.value } ?: return null
    val speed = when {
        worstScore >= ChainHealthThresholds.CONNECTION_HIGH_FROM -> return null
        worstScore >= ChainHealthThresholds.CONNECTION_GOOD_FROM -> Speed.Good
        worstScore >= ChainHealthThresholds.CONNECTION_FAIR_FROM -> Speed.Fair
        else -> Speed.Low
    }
    return ChainHealthIndicator.ConnectionSpeed(speed)
}

private fun ChainMetricReading.isConnectionSpeed(): Boolean = when (this) {
    is ChainMetricReading.PendingRequestLatency, is ChainMetricReading.ResponseLatency -> true
    is ChainMetricReading.BlockLatency, is ChainMetricReading.BlockProduction, is ChainMetricReading.FinalityGap -> false
}
