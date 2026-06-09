package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.models

import androidx.compose.runtime.Immutable
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel

@Immutable
data class CoinageUiState(
    val tokensState: TokensState,
    val autoFundAvailable: Boolean,
    val fundInProgress: Boolean,
    val actionsEnabled: Boolean,
    val coinageWidgetsEnabled: Boolean,
    val testnetFundEnabled: Boolean
) {
    @Immutable
    data class TokensState(
        val totalBalance: TokenAmountModel,
        val spendableSecuredBalance: TokenAmountModel,
        val spendableDegradedBalance: TokenAmountModel,
        val pendingBalance: TokenAmountModel,
        val coinList: List<Coin>,
        val voucherList: List<RecyclerVoucher>
    )
}
