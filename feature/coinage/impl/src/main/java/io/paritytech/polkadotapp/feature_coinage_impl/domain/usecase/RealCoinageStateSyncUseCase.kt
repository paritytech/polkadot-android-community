package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.utils.mapToUnit
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.HandedOffCoin
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.OwnAsset
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageStateSyncUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.CoinKeypairDerivation
import io.paritytech.polkadotapp.feature_coinage_impl.data.derivation.getDerivedAccountIds
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetKind
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.LedgerAsset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import kotlin.time.Instant

class RealCoinageStateSyncUseCase @Inject constructor(
    private val assetLedger: CoinageAssetLedger,
    private val coinKeypairDerivation: CoinKeypairDerivation,
) : CoinageStateSyncUseCase {
    override suspend fun getHandedOffCoins(committedAfter: Instant?): List<HandedOffCoin> {
        return assetLedger.getCommittedCoinHandoffs(committedAfter)
    }

    override suspend fun applyHandedOffCoins(coins: List<HandedOffCoin>) {
        val indices = coins.map { it.index }
        val publicKeys = coinKeypairDerivation.getDerivedAccountIds(indices)

        val assets = indices.zip(publicKeys) { index, publicKey ->
            LedgerAsset(kind = CoinageAssetKind.COIN, asset = OwnAsset.Coin(index), publicKey = publicKey)
        }
        assetLedger.insertSyncedHandoffs(assets)
    }

    override fun observeHandoffChanges(): Flow<Unit> {
        return assetLedger.observeLastHandoffCommit().distinctUntilChanged().mapToUnit()
    }
}
