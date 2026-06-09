package io.paritytech.polkadotapp.feature_tokens_api.presentation.model

import java.math.BigDecimal

/**
 * To create TokenAmountModel use TokenAmountMapper
 */
interface TokenAmountModel {
    val amount: BigDecimal

    val appearance: TokenSymbolAppearance

    companion object {
        val mock: TokenAmountModel get() = mock()

        fun mock(
            value: Int = 1000,
            appearance: TokenSymbolAppearance = TokenSymbolAppearance.DigitalDollar
        ): TokenAmountModel = object : TokenAmountModel {
            override val amount = value.toBigDecimal()
            override val appearance = TokenSymbolAppearance.DigitalDollar
        }
    }
}
