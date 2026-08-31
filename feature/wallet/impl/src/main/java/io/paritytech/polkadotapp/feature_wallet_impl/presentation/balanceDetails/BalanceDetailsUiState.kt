package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel

@Immutable
data class BalanceDetailsUiState(
    val totalBalance: TokenAmountModel,
    val availablePrivate: TokenAmountModel,
    val exposed: TokenAmountModel,
    val canSpendExposed: Boolean,
    val notAvailable: TokenAmountModel,
)
