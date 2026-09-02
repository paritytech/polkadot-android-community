package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance

data class BalanceBreakdown(
    val asset: Chain.Asset,
    val availablePrivate: Balance,
    val exposed: Balance,
    /** False when the chosen strategy will not part with [exposed], which changes how it is labelled. */
    val canSpendExposed: Boolean,
    val notAvailable: Balance,
)
