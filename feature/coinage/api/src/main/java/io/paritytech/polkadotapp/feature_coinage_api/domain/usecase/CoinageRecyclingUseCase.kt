package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.Coin

interface CoinageRecyclingUseCase {
    suspend operator fun invoke(): Result<Unit>

    suspend fun recycle(coins: List<Coin>): Result<Unit>
}
