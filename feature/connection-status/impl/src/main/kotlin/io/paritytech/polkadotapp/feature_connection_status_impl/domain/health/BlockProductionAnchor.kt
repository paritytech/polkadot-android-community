package io.paritytech.polkadotapp.feature_connection_status_impl.domain.health

import kotlin.time.Duration

/**
 * How long the chain's own clock says it took to produce [blocks] blocks ending at [headHeight].
 * Measured once per connection so production is known before a window of observation has passed.
 */
data class BlockProductionAnchor(
    val headHeight: Int,
    val blocks: Int,
    val span: Duration,
)
