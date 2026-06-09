package io.paritytech.polkadotapp.feature_members_impl.data.repository

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.storage.source.StorageDataSources
import io.paritytech.polkadotapp.chains.storage.source.pickForDataConsistencyRequirement
import io.paritytech.polkadotapp.chains.storage.source.query.WithRawValue
import io.paritytech.polkadotapp.chains.storage.source.query.metadata
import io.paritytech.polkadotapp.chains.storage.source.queryCatching
import io.paritytech.polkadotapp.chains.storage.source.subscribeCatching
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCommitmentRecord
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersSubscriberRepository
import io.paritytech.polkadotapp.feature_members_impl.data.network.blockchain.api.membersSubscriber
import io.paritytech.polkadotapp.feature_members_impl.data.network.blockchain.api.ringRoots
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RealMembersSubscriberRepository @Inject constructor(
    private val storageDataSources: StorageDataSources,
) : MembersSubscriberRepository {
    override suspend fun getRingRoots(
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        consistency: CacheableDataConsistency,
        blockHash: BlockHash?,
    ): Result<List<RingCommitmentRecord>?> {
        return storageDataSources.pickForDataConsistencyRequirement(consistency).queryCatching(chainId, blockHash) {
            metadata.membersSubscriber.ringRoots.query(collectionId, ringIndex)
        }
    }

    override fun subscribeRingRoots(
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
    ): Flow<Result<WithRawValue<List<RingCommitmentRecord>?>>> {
        return storageDataSources.remote.subscribeCatching(chainId) {
            metadata.membersSubscriber.ringRoots.observeWithRaw(collectionId, ringIndex)
        }
    }
}
