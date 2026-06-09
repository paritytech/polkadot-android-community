package io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment

import kotlinx.coroutines.flow.StateFlow

interface SendPaymentContract {
    val state: StateFlow<SendPaymentUiState>

    fun onInputChange(value: String)
    fun onRecipientSelect(recipient: PaymentSearchResultUiModel)

    fun onPasteClick()

    fun onScannerClick()

    fun onBackClick()
}
