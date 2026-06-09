package io.paritytech.polkadotapp.feature_members_impl.data.network.blockchain.api

import io.novasama.substrate_sdk_android.runtime.metadata.RuntimeMetadata
import io.novasama.substrate_sdk_android.runtime.metadata.module
import io.novasama.substrate_sdk_android.runtime.metadata.module.Module
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableModule
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry2
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage2
import io.paritytech.polkadotapp.chains.util.Modules
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCollectionId
import io.paritytech.polkadotapp.feature_members_api.data.model.RingCommitmentRecord
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex

@JvmInline
value class MembersSubscriberApi(override val module: Module) : QueryableModule

context(WithRuntime)
val RuntimeMetadata.membersSubscriber: MembersSubscriberApi
    get() = MembersSubscriberApi(module(Modules.MEMBERS_SUBSCRIBER))

context(WithRuntime)
val MembersSubscriberApi.ringRoots: QueryableStorageEntry2<RingCollectionId, RingIndex, List<RingCommitmentRecord>>
    get() = storage2("RingRoots")
