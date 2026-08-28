package io.paritytech.polkadotapp.feature_coinage_impl.data.storage

import io.paritytech.polkadotapp.common.data.storage.SingleValueStorage
import io.paritytech.polkadotapp.common.data.storage.SingleValueStorageFactory
import io.paritytech.polkadotapp.common.data.storage.preferences.store.PreferencesSingleValueSerializer
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageInstanceId
import java.math.BigInteger
import javax.inject.Inject

class CachedCoinageAssetUnit(
    val instanceId: CoinageInstanceId,
    val assetUnit: BigInteger
)

interface CoinageAssetUnitStorage : SingleValueStorage<CachedCoinageAssetUnit>

class RealCoinageAssetUnitStorage @Inject constructor(
    factory: SingleValueStorageFactory,
) : CoinageAssetUnitStorage,
    SingleValueStorage<CachedCoinageAssetUnit> by factory.preferences(
        key = "CoinageAssetUnitStorage",
        serializer = PreferencesSingleValueSerializer.from(
            toString = { "${it.instanceId}:${it.assetUnit}" },
            fromString = { raw ->
                val (instanceId, assetUnit) = raw.split(':')
                CachedCoinageAssetUnit(instanceId.toUInt(), assetUnit.toBigInteger())
            },
        ),
        default = null,
    )
