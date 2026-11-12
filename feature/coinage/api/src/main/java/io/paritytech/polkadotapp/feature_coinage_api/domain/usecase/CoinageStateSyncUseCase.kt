package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface CoinageStateSyncUseCase {
    suspend fun getHandedOffCoins(committedAfter: Instant?): List<HandedOffCoin>

    fun observeHandoffChanges(): Flow<Unit>
}
