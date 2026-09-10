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
 * What one chain's indicator draws. Connectivity outranks health: a chain that is not connected is
 * [Disconnected] or [Connecting] whatever its last score was.
 */
sealed interface ChainHealthIndicator {
    /** Every metric within tolerance: a solid disc, no colour. */
    data object Healthy : ChainHealthIndicator

    /**
     * Connected but at least one metric is out of tolerance. [fraction] (0..1) is the arc the ring
     * draws; [tone] is its colour, [Tone.Error] whenever the chain itself (block production or
     * finality) is behind, otherwise graded by how slow the connection is.
     */
    data class Degraded(
        val tone: Tone,
        val fraction: Float,
    ) : ChainHealthIndicator

    data object Connecting : ChainHealthIndicator

    data object Disconnected : ChainHealthIndicator

    enum class Tone {
        Neutral,
        Warning,
        Error,
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
