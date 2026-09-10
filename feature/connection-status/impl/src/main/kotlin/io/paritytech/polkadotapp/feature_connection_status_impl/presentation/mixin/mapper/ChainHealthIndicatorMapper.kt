package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Speed

private const val ADEQUATE_FROM = 70
private const val UNUSABLE_BELOW = 40

fun ChainHealth.toIndicator(): ChainHealthIndicator = when (connection) {
    ChainConnectionPresentation.Disconnected -> ChainHealthIndicator.Disconnected
    ChainConnectionPresentation.Connecting -> ChainHealthIndicator.Connecting
    ChainConnectionPresentation.Connected -> when {
        nodeUnresponsive() -> ChainHealthIndicator.Disconnected
        else -> outage() ?: slowConnection() ?: ChainHealthIndicator.Healthy
    }
}

private fun ChainHealth.nodeUnresponsive(): Boolean = readings
    .filterIsInstance<ChainMetricReading.PendingRequestLatency>()
    .any { it.score.value == ChainHealthScore.MIN_VALUE }

private fun ChainHealth.outage(): ChainHealthIndicator.Outage? {
    val production = readings.filterIsInstance<ChainMetricReading.BlockProduction>().firstOrNull() ?: return null
    if (production.recentBlocks >= production.requiredBlocks) return null
    return ChainHealthIndicator.Outage(production.recentBlocks.toFloat() / production.expectedBlocks)
}

private fun ChainHealth.slowConnection(): ChainHealthIndicator.SlowConnection? {
    val worstScore = readings.filter { it.isConnectionSpeed() }.minOfOrNull { it.score.value } ?: return null
    return when {
        worstScore >= ADEQUATE_FROM -> null
        worstScore < UNUSABLE_BELOW -> ChainHealthIndicator.SlowConnection(Speed.Unusable)
        else -> ChainHealthIndicator.SlowConnection(Speed.Slow)
    }
}

private fun ChainMetricReading.isConnectionSpeed(): Boolean = when (this) {
    is ChainMetricReading.PendingRequestLatency, is ChainMetricReading.ResponseLatency -> true
    is ChainMetricReading.BlockLatency, is ChainMetricReading.BlockProduction, is ChainMetricReading.FinalityGap -> false
}
