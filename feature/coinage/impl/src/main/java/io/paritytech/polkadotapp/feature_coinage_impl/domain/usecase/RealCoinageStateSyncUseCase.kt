package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.utils.mapToUnit
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageStateSyncUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import kotlin.time.Instant

class RealCoinageStateSyncUseCase @Inject constructor(
    private val assetLedger: CoinageAssetLedger,
) : CoinageStateSyncUseCase {
    override suspend fun getHandedOffCoins(committedAfter: Instant?): List<HandedOffCoin> {
        return assetLedger.getCommittedCoinHandoffs(committedAfter)
    }

    override fun observeHandoffChanges(): Flow<Unit> {
        return assetLedger.observeLastHandoffCommit().distinctUntilChanged().mapToUnit()
    }
}
