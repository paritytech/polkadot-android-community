package io.paritytech.polkadotapp.feature_members_impl.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchDomainSize
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropy
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchProof
import io.paritytech.polkadotapp.bandersnatch_crypto.createProof
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.combineResults
import io.paritytech.polkadotapp.common.utils.requireNotNull
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.RingKeys
import io.paritytech.polkadotapp.feature_members_api.data.model.toDomainSize
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.getRingStatus
import io.paritytech.polkadotapp.feature_members_api.domain.MembershipProver
import io.paritytech.polkadotapp.feature_members_api.domain.model.MemberSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class RealMembershipProver @Inject constructor(
    private val membersRepository: MembersRepository,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
) : MembershipProver {
    override suspend fun proofMembership(
        member: MemberSource,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash?,
    ): Result<BandersnatchProof> {
        val entropyResult = runCancellableCatching { member.toEntropy() }
        val includedRingMembersResult = fetchIncludedRingMembers(chainId, collectionId, ringIndex, blockHash)
        val domainSizeResult = fetchDomainSize(chainId, collectionId)

        return combineResults(entropyResult, includedRingMembersResult, domainSizeResult) { entropy, ringMembers, domainSize ->
            entropy.createProof(ringMembers, message, context.value, domainSize)
        }
    }

    override suspend fun proofMembershipBatched(
        members: List<MemberSource>,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash?,
    ): Result<List<BandersnatchProof>> {
        val entropiesResult = runCancellableCatching { members.map { it.toEntropy() } }
        val includedRingMembersResult = fetchIncludedRingMembers(chainId, collectionId, ringIndex, blockHash)
        val domainSizeResult = fetchDomainSize(chainId, collectionId)

        return combineResults(entropiesResult, includedRingMembersResult, domainSizeResult) { entropies, ringMembers, domainSize ->
            entropies.map { entropy ->
                entropy.createProof(ringMembers, message, context.value, domainSize)
            }
        }
    }

    private suspend fun MemberSource.toEntropy(): BandersnatchEntropy {
        return when (this) {
            is MemberSource.Entropy -> bandersnatchEntropy
            is MemberSource.Account -> bandersnatchSecretsStorage.getEntropy(metaId)
        }
    }

    private suspend fun fetchIncludedRingMembers(
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash?,
    ): Result<RingKeys> = coroutineScope {
        val pageMembersDeferred = async {
            membersRepository.getRingKeys(
                chainId = chainId,
                collectionId = collectionId,
                ringIndex = ringIndex,
                consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
                blockHash = blockHash,
            )
        }
        val ringStatusDeferred = async {
            membersRepository.getRingStatus(
                chainId = chainId,
                collectionId = collectionId,
                ringIndex = ringIndex,
                consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
                blockHash = blockHash,
            ).requireNotNull()
        }

        combineResults(pageMembersDeferred.await(), ringStatusDeferred.await()) { members, status ->
            members.take(status.included)
        }
    }

    private suspend fun fetchDomainSize(
        chainId: ChainId,
        collectionId: RingCollectionId,
    ): Result<BandersnatchDomainSize> {
        return membersRepository.getCollection(
            chainId = chainId,
            collectionId = collectionId,
            consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
        ).map { it.ringSize.toDomainSize() }
    }
}
