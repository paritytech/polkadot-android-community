package io.paritytech.polkadotapp.feature_people_api.domain

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchProof
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.chains.network.binding.BlockHash
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex

interface PeopleMembershipProver {
    suspend fun proofPersonMembership(
        message: ByteArray,
        context: BandersnatchContext,
        chainId: ChainId,
        peopleCollection: PeopleCollection,
        at: BlockHash? = null,
    ): Result<PeopleMembershipProof>
}

class PeopleMembershipProof(
    val proof: BandersnatchProof,
    val ringIndex: RingIndex,
)
