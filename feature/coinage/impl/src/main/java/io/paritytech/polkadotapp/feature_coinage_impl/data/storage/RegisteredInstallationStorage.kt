package io.paritytech.polkadotapp.feature_coinage_impl.data.storage

import io.paritytech.polkadotapp.common.data.storage.SingleValueStorage
import io.paritytech.polkadotapp.common.data.storage.SingleValueStorageFactory
import io.paritytech.polkadotapp.common.data.storage.preferences.store.PreferencesSingleValueSerializer
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.OperationGroupId
import javax.inject.Inject

interface RegisteredInstallationStorage : SingleValueStorage<OperationGroupId>

class RealRegisteredInstallationStorage @Inject constructor(
    factory: SingleValueStorageFactory,
) : RegisteredInstallationStorage,
    SingleValueStorage<OperationGroupId> by factory.preferences(
        key = "CoinageRegisteredInstallationGroup",
        serializer = PreferencesSingleValueSerializer.from(
            toString = { it.value },
            fromString = ::OperationGroupId,
        ),
        default = null,
    )
