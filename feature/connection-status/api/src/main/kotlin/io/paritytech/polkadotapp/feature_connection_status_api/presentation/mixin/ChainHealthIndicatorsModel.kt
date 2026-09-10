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
    /** Everything adequate: a plain disc, no colour. */
    data object Healthy : ChainHealthIndicator

    /**
     * The chain produced fewer than five sixths of the blocks expected in the last 30 s. [fraction]
     * is recent / expected and is the share of the surround the dark red arc covers.
     */
    data class Outage(
        val fraction: Float,
    ) : ChainHealthIndicator

    /** Connected and producing blocks, but requests queue up: an unbroken yellow or red surround. */
    data class SlowConnection(
        val speed: Speed,
    ) : ChainHealthIndicator

    /** Reconnecting: the glyph fades in and out while the socket settles. */
    data object Connecting : ChainHealthIndicator

    /** No node responding, or a node that no longer answers requests: glyph and surround both dark grey. */
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
