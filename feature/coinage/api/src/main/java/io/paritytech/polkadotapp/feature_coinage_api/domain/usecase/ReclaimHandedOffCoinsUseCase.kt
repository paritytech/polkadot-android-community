package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import kotlinx.coroutines.flow.Flow

interface ReclaimHandedOffCoinsUseCase {
    suspend fun reclaim(coins: List<AccountId>, groupId: CoinageOperationGroupId): Result<Unit>

    fun subscribeFailedReclaims(groupId: CoinageOperationGroupId): Flow<Set<AccountId>>
}
