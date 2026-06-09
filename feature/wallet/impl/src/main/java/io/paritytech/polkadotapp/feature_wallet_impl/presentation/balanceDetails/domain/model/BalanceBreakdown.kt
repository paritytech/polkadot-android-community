package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance

data class BalanceBreakdown(
    val asset: Chain.Asset,
    val total: Balance,
    val availableNow: Balance,
    val availableNowSecured: Balance,
    val availableNowLowPrivacy: Balance,
    val availableSoon: Balance,
)
