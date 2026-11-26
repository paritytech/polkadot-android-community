package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import kotlinx.coroutines.flow.Flow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

interface ReclaimHandedOffCoinsUseCase {
    @OptIn(ExperimentalTime::class)
    suspend fun reclaim(coins: List<AccountId>, groupId: CoinageOperationGroupId, handedOffAt: Instant): Result<Unit>

    fun subscribeFailedReclaims(groupId: CoinageOperationGroupId): Flow<Set<AccountId>>
}
