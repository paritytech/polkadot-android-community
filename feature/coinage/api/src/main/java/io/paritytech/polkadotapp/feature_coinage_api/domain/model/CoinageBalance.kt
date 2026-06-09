package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.chains.network.binding.Balance

data class CoinageBalance(
    val spendableBalance: SpendableBalance,
    val pendingBalance: Balance,
) {
    data class SpendableBalance(
        val degraded: Balance,
        val secured: Balance
    ) {
        val total: Balance = degraded + secured
    }

    val totalBalance: Balance = spendableBalance.total + pendingBalance
}
