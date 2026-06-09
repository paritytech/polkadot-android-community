package io.paritytech.polkadotapp.feature_members_api.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchProof
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex
import io.paritytech.polkadotapp.feature_members_api.domain.model.MemberSource

interface MembershipProver {
    suspend fun proofMembership(
        member: MemberSource,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash? = null,
    ): Result<BandersnatchProof>

    suspend fun proofMembershipBatched(
        members: List<MemberSource>,
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        collectionId: RingCollectionId,
        ringIndex: RingIndex,
        blockHash: BlockHash? = null,
    ): Result<List<BandersnatchProof>>
}
