package io.paritytech.polkadotapp.feature_transaction_storage_impl.data.signer.origins

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.feature_members_api.data.repository.MembersRepository
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCollection
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleMembershipProver
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.extension.ClaimLongTermStorage
import io.paritytech.polkadotapp.feature_transaction_storage_impl.data.extension.longTermStorageClaim
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.SetTransactionExtensionOrigin
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import javax.inject.Inject

class RealTransactionStorageOrigins @Inject constructor(
    private val peopleMembershipProver: PeopleMembershipProver,
    private val membersRepository: MembersRepository,
    private val chainRegistry: ChainRegistry,
) : TransactionStorageOrigins {
    override suspend fun asResourcesLongTermStorage(
        period: UInt,
        counter: UByte,
        collection: PeopleCollection,
    ): TransactionOrigin {
        val context = BandersnatchContext.longTermStorageClaim(period, counter)
        val extension = ClaimLongTermStorage(
            context = context,
            collection = collection,
            peopleMembershipProver = peopleMembershipProver,
            membersRepository = membersRepository,
            chainRegistry = chainRegistry,
        )
        return SetTransactionExtensionOrigin(TransactionSignerSource.None, extension)
    }
}
