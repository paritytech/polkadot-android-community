package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.chains.network.binding.Balance

sealed class CoinageTransferDetection {
    data object Detecting : CoinageTransferDetection()

    data class Detected(val amount: Balance) : CoinageTransferDetection()

    data class Transferred(val amount: Balance) : CoinageTransferDetection()

    sealed class Error : CoinageTransferDetection() {
        data object Detection : Error()
        data object Transfer : Error()
    }
}

fun CoinageTransferDetection.detectedOrNull(): Balance? = when (this) {
    is CoinageTransferDetection.Detected -> amount
    is CoinageTransferDetection.Transferred -> amount
    else -> null
}

fun CoinageTransferDetection.isTerminal(): Boolean {
    return this is CoinageTransferDetection.Error.Transfer || this is CoinageTransferDetection.Transferred
}
