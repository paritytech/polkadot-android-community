package io.paritytech.polkadotapp.feature_wallet_impl.presentation.balanceDetails

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel

/**
 * A null amount is a bucket the user has nothing in. Filtered here rather than in the sheet so the rule
 * lives in one place — a row with a zero beside it says nothing worth the space it takes.
 */
@Immutable
data class BalanceDetailsUiState(
    val availablePrivate: TokenAmountModel?,
    val exposed: TokenAmountModel?,
    val canSpendExposed: Boolean,
    val notAvailable: TokenAmountModel?,
)
