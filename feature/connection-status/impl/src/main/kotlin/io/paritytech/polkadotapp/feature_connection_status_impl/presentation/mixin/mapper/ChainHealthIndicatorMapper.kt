package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper

import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealthScore
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator.Tone

private const val HEALTHY_FROM = 90
private const val NEUTRAL_FROM = 70
private const val WARNING_FROM = 40

fun ChainHealth.toIndicator(): ChainHealthIndicator = when (connection) {
    ChainConnectionPresentation.Disconnected -> ChainHealthIndicator.Disconnected
    ChainConnectionPresentation.Connecting -> ChainHealthIndicator.Connecting
    ChainConnectionPresentation.Connected -> connectedIndicator()
}

private fun ChainHealth.connectedIndicator(): ChainHealthIndicator = when {
    score.value >= HEALTHY_FROM -> ChainHealthIndicator.Healthy
    readings.any { it.isChainSide() && it.isDegraded() } -> ChainHealthIndicator.Degraded(Tone.Error, score.fraction)
    else -> ChainHealthIndicator.Degraded(score.connectionTone(), score.fraction)
}

private fun ChainMetricReading.isDegraded(): Boolean = score.value < HEALTHY_FROM

private fun ChainMetricReading.isChainSide(): Boolean = when (this) {
    is ChainMetricReading.BlockLatency, is ChainMetricReading.FinalityGap -> true
    is ChainMetricReading.PendingRequestLatency, is ChainMetricReading.ResponseLatency -> false
}

private fun ChainHealthScore.connectionTone(): Tone = when {
    value >= NEUTRAL_FROM -> Tone.Neutral
    value >= WARNING_FROM -> Tone.Warning
    else -> Tone.Error
}
