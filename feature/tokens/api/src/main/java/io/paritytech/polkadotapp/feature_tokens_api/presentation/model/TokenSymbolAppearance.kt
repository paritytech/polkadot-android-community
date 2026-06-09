package io.paritytech.polkadotapp.feature_tokens_api.presentation.model

sealed interface TokenSymbolAppearance {
    object DigitalDollar : TokenSymbolAppearance {
        const val SYMBOL = "CASH"
    }

    class Symbol(val symbol: String) : TokenSymbolAppearance
}
