package io.paritytech.polkadotapp.feature_prices_api.presentation.mapper.model

import java.math.BigDecimal

class FiatAmountModel(
    val fiatAmount: BigDecimal,
    val currencyDisplay: String
)
