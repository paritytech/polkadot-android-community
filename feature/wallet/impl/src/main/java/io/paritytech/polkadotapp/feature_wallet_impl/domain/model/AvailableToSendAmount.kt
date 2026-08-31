package io.paritytech.polkadotapp.feature_wallet_impl.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.amountFromPlanks

data class AvailableToSendAmount(
    val available: Balance,
    val chainAsset: Chain.Asset
)

fun AvailableToSendAmount.spendablePlanks() = chainAsset.amountFromPlanks(available)
