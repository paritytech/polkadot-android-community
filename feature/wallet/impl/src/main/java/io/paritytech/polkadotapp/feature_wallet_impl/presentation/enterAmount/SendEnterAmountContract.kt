package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount

import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.common.presentation.validation.ValidationMixin
import kotlinx.coroutines.flow.StateFlow

interface SendEnterAmountContract {
    val state: StateFlow<LoadingState<SendEnterAmountUiState>>

    val sendValidationMixin: ValidationMixin

    fun onConfirmClick()

    fun onBackClick()

    fun onNewInput(value: String)
}
