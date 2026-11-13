package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclingVerdicts
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.RecyclingStrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.TrackedVoucher

internal data class CoinageEvaluationInput(
    val coins: List<TrackedCoin>,
    val vouchers: List<TrackedVoucher>,
    val strategyType: RecyclingStrategyType,
    val verdicts: RecyclingVerdicts,
)
