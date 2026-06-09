package io.paritytech.polkadotapp.feature_products_impl.presentation.topUpRequest

import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import kotlinx.coroutines.flow.StateFlow

interface TopUpRequestContract {
    val state: StateFlow<LoadingState<TopUpRequestUiState>>

    fun onClaimClicked()
}

data class TopUpRequestUiState(
    val productId: String,
    val amount: TokenAmountModel,
    val claiming: Boolean,
    val amountMismatch: Boolean,
)
