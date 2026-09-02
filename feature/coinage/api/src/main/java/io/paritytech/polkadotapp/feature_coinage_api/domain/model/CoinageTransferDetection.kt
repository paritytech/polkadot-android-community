package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.chains.network.binding.Balance

/**
 * How far a claim of coins a peer handed us has got.
 */
sealed interface CoinageTransferDetection {
    /** Nothing the keys control is on chain yet, so there is nothing to claim. */
    data object Detecting : CoinageTransferDetection

    /**
     * Claims are under way and none of the coins is ours yet. No failures occurred so far.
     */
    data object Claiming : CoinageTransferDetection

    /**
     * [claimed] is ours, and the coins still missing are waiting are retrying because an attempt at them
     * failed.
     *
     * Only reported when something actually went wrong — a payment merely arriving in pieces reports as
     * [Claiming].
     */
    data class ClaimingRest(val claimed: Balance) : CoinageTransferDetection

    /**
     * Every coin is ours.
     *
     * [finalized] is false while the claims are only in a best-chain block. When true, this state is terminal. When false, it can be downgraded
     * to [Claiming], [Detecting] or [ClaimingRest]
     */
    data class Claimed(val amount: Balance, val finalized: Boolean) : CoinageTransferDetection

    /** Claiming is over and [claimed] is all that will ever arrive. Only ever the last word. */
    data class ClaimedPartially(val claimed: Balance) : CoinageTransferDetection

    /**
     * Nothing was claimed and nothing more will be tried. Only ever the last word.
     */
    data object NotClaimed : CoinageTransferDetection
}
