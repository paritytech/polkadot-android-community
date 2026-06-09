package io.paritytech.polkadotapp.feature_transaction_storage_impl.data.extension

import io.novasama.substrate_sdk_android.hash.Hasher.blake2b256
import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.runtime.extrinsic.v5.transactionExtension.InheritedImplication
import io.novasama.substrate_sdk_android.runtime.extrinsic.v5.transactionExtension.TransactionExtension
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.getChainIdByGenesisHashOrThrow
import io.paritytech.polkadotapp.chains.util.findGenesisHashOrThrow
import io.paritytech.polkadotapp.common.data.cache.CacheableDataConsistency
import io.paritytech.polkadotapp.common.utils.requireNotNull
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_members_api.data.repository.getRingRoot
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleMembershipProver
import io.paritytech.polkadotapp.feature_people_api.domain.toRingCollectionId

class ClaimLongTermStorage(
    private val context: BandersnatchContext,
    private val collection: PeopleCollection,
    private val peopleMembershipProver: PeopleMembershipProver,
    private val membersRepository: MembersRepository,
    private val chainRegistry: ChainRegistry,
) : TransactionExtension {
    override val name: String = "AsResources"

    override suspend fun implicit(): Any? = null

    override suspend fun explicit(
        inheritedImplication: InheritedImplication,
        runtimeSnapshot: RuntimeSnapshot,
    ): Any? {
        val chainId = chainRegistry.getChainIdByGenesisHashOrThrow(inheritedImplication.findGenesisHashOrThrow())
        val message = inheritedImplication.encoded().blake2b256()

        val proofResult = peopleMembershipProver.proofPersonMembership(
            message = message,
            context = context,
            chainId = chainId,
            peopleCollection = collection,
        ).getOrThrow()

        val revision = membersRepository.getRingRoot(
            chainId = chainId,
            collectionId = collection.toRingCollectionId(),
            ringIndex = proofResult.ringIndex,
            consistency = CacheableDataConsistency.CONSISTENT_WITH_REMOTE,
        ).requireNotNull().getOrThrow().revision

        return AsResourcesInfoScale.ClaimLongTermStorage(
            proof = proofResult.proof,
            ringIndex = proofResult.ringIndex,
            revision = revision,
            collection = collection.toScale(),
        ).toEncodableInstance()
    }
}

private fun PeopleCollection.toScale(): MembershipCollectionScale = when (this) {
    PeopleCollection.People -> MembershipCollectionScale.People
    PeopleCollection.LitePeople -> MembershipCollectionScale.LitePeople
}
