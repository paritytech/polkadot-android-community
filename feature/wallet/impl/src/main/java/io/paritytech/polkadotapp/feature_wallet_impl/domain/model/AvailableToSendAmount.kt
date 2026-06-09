package io.paritytech.polkadotapp.feature_wallet_impl.domain.model

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainAssetWithAmount
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageBalance.SpendableBalance

data class AvailableToSendAmount(
    val spendable: SpendableBalance,
    val chainAsset: Chain.Asset
)

fun AvailableToSendAmount.totalChainWithAssetAmount() = ChainAssetWithAmount(
    chainAsset = chainAsset,
    amount = spendable.total
)

fun AvailableToSendAmount.spendablePlanks() = chainAsset.amountFromPlanks(spendable.total)
