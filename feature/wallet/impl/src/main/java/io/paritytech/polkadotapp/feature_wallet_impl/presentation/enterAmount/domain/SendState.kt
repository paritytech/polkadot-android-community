package io.paritytech.polkadotapp.feature_wallet_impl.presentation.enterAmount.domain

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection

sealed interface SendState {
    data class Settling(val detection: CoinageTransferDetection) : SendState

    data object Complete : SendState

    data class Failed(val error: Throwable) : SendState
}
