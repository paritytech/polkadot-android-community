package io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlin.time.Duration

private const val FULL_FROM = 5f / 6f
private const val NEUTRAL_FROM = 0.5f
private const val WARNING_FROM = 0.25f

enum class ChainGlyph {
    People,
    AssetHub,
    Bulletin,
}

/**
 * What one chain's indicator draws, in the priority the health rules give them: reaching the chain
 * comes first, then what it is producing. You cannot judge a chain you cannot see.
 */
sealed interface ChainHealthIndicator {
    /**
     * Connected, with [share] of the expected blocks produced over the sampling window, and the block
     * interval that share implies. The ring draws [share] of the circle directly.
     */
    data class Producing(
        val share: Float,
        val blockInterval: Duration,
    ) : ChainHealthIndicator {
        val band: Band
            get() = Band.of(share)
    }

    /** Connected, producing nothing at all. */
    data object Outage : ChainHealthIndicator

    data object Connecting : ChainHealthIndicator

    /** The connection to the node will not hold, or the node stopped answering. */
    data object Broken : ChainHealthIndicator

    /** The device has no network. */
    data object NoInternet : ChainHealthIndicator

    /** The quarters of the ring the design changes colour on. [Full] closes it into a solid disc. */
    enum class Band {
        Full,
        Neutral,
        Warning,
        Error,
        ;

        companion object {
            fun of(share: Float): Band = when {
                share >= FULL_FROM -> Full
                share >= NEUTRAL_FROM -> Neutral
                share >= WARNING_FROM -> Warning
                else -> Error
            }
        }
    }

    companion object {
        /** A share of zero is not slow production but none at all, and has no interval to report. */
        fun producing(share: Float, blockTime: Duration): ChainHealthIndicator = when {
            share <= 0f -> Outage
            else -> share.coerceAtMost(1f).let { Producing(share = it, blockInterval = blockTime / it.toDouble()) }
        }
    }
}

@Immutable
data class ChainHealthIndicatorsModel(
    val chains: ImmutableList<ChainHealthItemModel>,
)

@Immutable
data class ChainHealthItemModel(
    val chainName: String,
    val glyph: ChainGlyph,
    val indicator: ChainHealthIndicator,
)
