package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.common.domain.model.AccountId

class CoinUpdate(
    val accountId: AccountId,
    val age: Int,
    val spentState: Coin.SpentState
)
