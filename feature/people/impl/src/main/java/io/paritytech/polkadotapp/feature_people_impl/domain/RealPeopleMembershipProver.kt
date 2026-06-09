package io.paritytech.polkadotapp.feature_people_impl.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.requireNotNull
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getCandidateAccount
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getMemberKey
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.data.model.includedOrFailure
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.getMember
import io.paritytech.polkadotapp.feature_members_api.domain.MembershipProver
import io.paritytech.polkadotapp.feature_members_api.domain.model.MemberSource
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleMembershipProof
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleMembershipProver
import io.paritytech.polkadotapp.feature_people_api.domain.toRingCollectionId
import javax.inject.Inject

class RealPeopleMembershipProver @Inject constructor(
    private val accountRepository: AccountRepository,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
    private val membersRepository: MembersRepository,
    private val membershipProver: MembershipProver,
) : PeopleMembershipProver {
    override suspend fun proofPersonMembership(
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        peopleCollection: PeopleCollection,
        at: BlockHash?,
    ): Result<PeopleMembershipProof> {
        val metaId = resolveMetaId(peopleCollection)
        val collectionId = peopleCollection.toRingCollectionId()

        return fetchPersonRingIndex(chainId, metaId, collectionId, at).flatMap { ringIndex ->
            membershipProver.proofMembership(
                member = MemberSource.Account(metaId),
                message = message,
                context = context,
                chainId = chainId,
                collectionId = collectionId,
                ringIndex = ringIndex,
                blockHash = at
            ).map { proof -> PeopleMembershipProof(proof, ringIndex) }
        }
    }

    private suspend fun resolveMetaId(peopleCollection: PeopleCollection): Long = when (peopleCollection) {
        PeopleCollection.People -> accountRepository.getCandidateAccount().id
        PeopleCollection.LitePeople -> accountRepository.getWalletAccount().id
    }

    private suspend fun fetchPersonRingIndex(
        chainId: ChainId,
        metaId: Long,
        collectionId: RingCollectionId,
        blockHash: BlockHash?,
    ): Result<RingIndex> {
        val memberKey = bandersnatchSecretsStorage.getMemberKey(metaId)

        return membersRepository.getMember(
            chainId = chainId,
            collectionId = collectionId,
            key = memberKey,
            consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
            blockHash = blockHash,
        )
            .requireNotNull()
            .flatMap { it.includedOrFailure() }
            .map { it.ringIndex }
    }
}
