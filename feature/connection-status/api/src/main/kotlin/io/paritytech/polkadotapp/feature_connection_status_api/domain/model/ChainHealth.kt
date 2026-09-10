package io.paritytech.polkadotapp.feature_connection_status_api.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId

/**
 * Aggregated health of a single chain: the smoothed socket state and one reading per probe. A chain can
 * be [ChainConnectionPresentation.Connected] while its readings say it is stalled; folding the two into
 * one indicator is the presentation layer's job.
 */
data class ChainHealth(
    val chainId: ChainId,
    val chainName: String,
    val connection: ChainConnectionPresentation,
    val readings: List<ChainMetricReading>,
)
