package io.paritytech.polkadotapp.feature_coinage_impl.data.repository

import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstanceId
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.coinage
import io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain.instances
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CachedCoinageAssetUnit
import io.paritytech.polkadotapp.feature_coinage_impl.data.storage.CoinageAssetUnitStorage
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import java.math.BigInteger
import javax.inject.Inject

interface CoinageInstanceRepository {
    suspend fun assetUnit(instanceId: CoinageInstanceId): Result<BigInteger>
}

class RealCoinageInstanceRepository @Inject constructor(
    @param:RemoteSourceQualifier private val storageDataSource: StorageDataSource,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val assetUnitStorage: CoinageAssetUnitStorage,
) : CoinageInstanceRepository {
    override suspend fun assetUnit(instanceId: CoinageInstanceId): Result<BigInteger> {
        val cached = assetUnitStorage.getValue()?.takeIf { it.instanceId == instanceId }
        if (cached != null) return Result.success(cached.assetUnit)

        return runCatching {
            val record = storageDataSource.query(chainAssetProvider.chain().id) {
                metadata.coinage.instances.query(instanceId.toLong().toBigInteger())
            } ?: error("Coinage instance $instanceId is not registered on chain")

            record.assetUnit.also { assetUnitStorage.saveValue(CachedCoinageAssetUnit(instanceId, it)) }
        }
    }
}
