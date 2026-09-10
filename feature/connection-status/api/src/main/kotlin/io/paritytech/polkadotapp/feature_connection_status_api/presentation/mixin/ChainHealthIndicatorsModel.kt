package io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainMetricReading
import kotlinx.collections.immutable.ImmutableList

/** Stable identity for a monitored chain's inner glyph; the widget maps each to a drawable. */
enum class ChainGlyph {
    People,
    AssetHub,
    Bulletin,
}

/**
 * What one chain's indicator draws, in the priority the health rules give them: connectivity first,
 * then block production, then connection speed; [Healthy] only when none of them has anything to say.
 */
sealed interface ChainHealthIndicator {
    /** Every metric within tolerance. */
    data object Healthy : ChainHealthIndicator

    /** The chain produced fewer of the blocks expected in the recent window than the health rules require. */
    data class Outage(
        val recentBlocks: Int,
        val expectedBlocks: Int,
    ) : ChainHealthIndicator

    /** Connected and producing blocks, but requests queue up. */
    data class SlowConnection(
        val speed: Speed,
    ) : ChainHealthIndicator

    /** The socket is re-establishing. */
    data object Connecting : ChainHealthIndicator

    /** No node responding, or a connected node that no longer answers requests. */
    data object Disconnected : ChainHealthIndicator

    enum class Speed {
        Slow,
        Unusable,
    }
}

@Immutable
data class ChainHealthIndicatorsModel(
    val chains: ImmutableList<ChainHealthItemModel>,
)

@Immutable
data class ChainHealthItemModel(
    val chainId: ChainId,
    val chainName: String,
    val glyph: ChainGlyph,
    val connection: ChainConnectionPresentation,
    val indicator: ChainHealthIndicator,
    val readings: ImmutableList<ChainMetricReading>,
)
