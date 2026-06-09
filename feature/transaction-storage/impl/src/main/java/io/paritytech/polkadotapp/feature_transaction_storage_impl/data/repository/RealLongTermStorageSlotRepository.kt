package io.paritytech.polkadotapp.feature_transaction_storage_impl.data.repository

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchAlias
import io.paritytech.polkadotapp.chains.di.RemoteSourceQualifier
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.multiNetwork.withRuntime
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSource
import io.paritytech.polkadotapp.common.utils.mapToSet
import io.paritytech.polkadotapp.common.utils.scale.BigEndianU32Scale
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.blockchain.longTermStorageClaimsPerPeriod
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.blockchain.longTermStoragePeriodDuration
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.blockchain.ltsResources
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.blockchain.spentLongTermStorageAliases
import javax.inject.Inject

class RealLongTermStorageSlotRepository @Inject constructor(
    @RemoteSourceQualifier private val storageDataSource: StorageDataSource,
    private val chainRegistry: ChainRegistry,
) : LongTermStorageSlotRepository {
    override suspend fun periodDurationSeconds(chainId: ChainId): UInt {
        return chainRegistry.withRuntime(chainId) {
            runtime.metadata.ltsResources.longTermStoragePeriodDuration
        }
    }

    override suspend fun maxClaimsPerPeriod(chainId: ChainId): UByte {
        return chainRegistry.withRuntime(chainId) {
            runtime.metadata.ltsResources.longTermStorageClaimsPerPeriod
        }
    }

    override suspend fun spentAliases(
        chainId: ChainId,
        period: UInt,
        candidates: List<BandersnatchAlias>,
    ): Set<BandersnatchAlias> {
        if (candidates.isEmpty()) return emptySet()
        return storageDataSource.query(chainId) {
            val periodKey = BigEndianU32Scale(period)
            val keys = candidates.map { periodKey to it }
            runtime.metadata.ltsResources.spentLongTermStorageAliases.findExistingKeys(keys)
                .mapToSet { (_, alias) -> alias }
        }
    }
}
