package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface CoinageStateSyncUseCase {
    suspend fun getHandedOffCoins(committedAfter: Instant?): List<HandedOffCoin>

    suspend fun applyHandedOffCoins(coins: List<HandedOffCoin>)

    suspend fun isHandedOffFromThisInstallation(coins: List<AccountId>): Boolean

    fun observeHandoffChanges(): Flow<Unit>
}
