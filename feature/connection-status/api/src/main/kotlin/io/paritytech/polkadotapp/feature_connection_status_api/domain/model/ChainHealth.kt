package io.paritytech.polkadotapp.feature_connection_status_api.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId

/**
 * Aggregated health of a single chain. [connection] is the smoothed socket state, [score] the lowest
 * of the [readings], which also back the details popover. The two axes are independent — a chain can be
 * [ChainConnectionPresentation.Connected] with a low [score] (connected but stalled); folding them into
 * one indicator is the presentation layer's job.
 */
data class ChainHealth(
    val chainId: ChainId,
    val chainName: String,
    val connection: ChainConnectionPresentation,
    val score: ChainHealthScore,
    val readings: List<ChainMetricReading>,
)
