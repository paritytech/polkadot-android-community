package io.paritytech.polkadotapp.feature_members_impl.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchDomainSize
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchEntropy
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchProof
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchProofResult
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.bandersnatch_crypto.createProof
import io.paritytech.polkadotapp.bandersnatch_crypto.memberKey
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.chains.repository.currentBlockHashCatching
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.combineResults
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapErrorNotInstance
import io.paritytech.polkadotapp.common.utils.requireNotNull
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.RingKeys
import io.paritytech.polkadotapp.feature_members_api.data.model.RingRoot
import io.paritytech.polkadotapp.feature_members_api.data.model.ringIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.toDomainSize
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.getMember
import io.paritytech.polkadotapp.feature_members_api.data.repository.getRingRoot
import io.paritytech.polkadotapp.feature_members_api.data.repository.getRingStatus
import io.paritytech.polkadotapp.feature_members_api.domain.MembershipProver
import io.paritytech.polkadotapp.feature_members_api.domain.RingVrfMembershipProof
import io.paritytech.polkadotapp.feature_members_api.domain.RingVrfProofError
import io.paritytech.polkadotapp.feature_members_api.domain.model.MemberSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class RealMembershipProver @Inject constructor(
    private val membersRepository: MembersRepository,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
    private val chainStateRepository: ChainStateRepository,
) : MembershipProver {
    override suspend fun proofMembership(
        member: MemberSource,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash?,
    ): Result<BandersnatchProofResult> {
        return pinnedBlockHash(chainId, blockHash).flatMap { pinnedBlockHash ->
            val entropyResult = runCancellableCatching { member.toEntropy() }
            val includedRingMembersResult = fetchIncludedRingMembers(chainId, collectionId, ringIndex, pinnedBlockHash)
            val domainSizeResult = fetchDomainSize(chainId, collectionId)

            combineResults(entropyResult, includedRingMembersResult, domainSizeResult) { entropy, ringMembers, domainSize ->
                entropy.createProof(ringMembers, message, context.value, domainSize)
            }
        }
    }

    override suspend fun createRingVrfProof(
        member: MemberSource,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        blockHash: BlockHash?,
    ): Result<RingVrfMembershipProof> {
        val pinnedBlockHashResult = pinnedBlockHash(chainId, blockHash)
        val memberKeyResult = runCancellableCatching { member.toEntropy().memberKey() }

        return combineResults(pinnedBlockHashResult, memberKeyResult) { blockHash, memberKey -> blockHash to memberKey }
            .flatMap { (pinnedBlockHash, memberKey) ->
                resolveMemberRing(chainId, collectionId, memberKey, pinnedBlockHash).flatMap { (ringIndex, ringRoot) ->
                    proofMembership(member, message, context, chainId, collectionId, ringIndex, pinnedBlockHash)
                        .map { RingVrfMembershipProof(it.proof, it.alias, ringIndex, ringRoot.revision) }
                }
            }
            .mapErrorNotInstance<_, RingVrfProofError> { RingVrfProofError.DataFetchFailed(it) }
    }

    private suspend fun resolveMemberRing(
        chainId: ChainId,
        collectionId: RingCollectionId,
        memberKey: BandersnatchPublicKey,
        pinnedBlockHash: BlockHash,
    ): Result<Pair<RingIndex, RingRoot>> {
        return membersRepository.getMember(
            chainId = chainId,
            collectionId = collectionId,
            key = memberKey,
            consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
            blockHash = pinnedBlockHash,
        ).flatMap { position ->
            val ringIndex = position?.ringIndex
                ?: return@flatMap Result.failure(RingVrfProofError.NotMember)

            membersRepository.getRingRoot(
                chainId = chainId,
                collectionId = collectionId,
                ringIndex = ringIndex,
                consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
                blockHash = pinnedBlockHash,
            ).flatMap { ringRoot ->
                ringRoot?.let { Result.success(ringIndex to it) }
                    ?: Result.failure(RingVrfProofError.RingNotFound)
            }
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
        return pinnedBlockHash(chainId, blockHash).flatMap { pinnedBlockHash ->
            val entropiesResult = runCancellableCatching { members.map { it.toEntropy() } }
            val includedRingMembersResult = fetchIncludedRingMembers(chainId, collectionId, ringIndex, pinnedBlockHash)
            val domainSizeResult = fetchDomainSize(chainId, collectionId)

            combineResults(entropiesResult, includedRingMembersResult, domainSizeResult) { entropies, ringMembers, domainSize ->
                entropies.map { entropy ->
                    entropy.createProof(ringMembers, message, context.value, domainSize).proof
                }
            }
        }
    }

    private suspend fun MemberSource.toEntropy(): BandersnatchEntropy {
        return when (this) {
            is MemberSource.Entropy -> bandersnatchEntropy
            is MemberSource.Account -> bandersnatchSecretsStorage.getEntropy(metaId)
        }
    }

    // Resolving the current block hash is a fallible RPC, so keep it inside the Result.
    private suspend fun pinnedBlockHash(chainId: ChainId, blockHash: BlockHash?): Result<BlockHash> {
        return blockHash?.let { Result.success(it) } ?: chainStateRepository.currentBlockHashCatching(chainId)
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
