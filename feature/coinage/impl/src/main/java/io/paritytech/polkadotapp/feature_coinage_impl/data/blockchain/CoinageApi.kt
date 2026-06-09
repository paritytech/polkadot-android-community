package io.paritytech.polkadotapp.feature_coinage_impl.data.blockchain

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.serializers.BigIntegerSerializable
import io.novasama.substrate_sdk_android.runtime.metadata.RuntimeMetadata
import io.novasama.substrate_sdk_android.runtime.metadata.module.Module
import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableModule
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry1
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry2
import io.paritytech.polkadotapp.chains.storage.source.query.api.QueryableStorageEntry3
import io.paritytech.polkadotapp.chains.storage.source.query.api.constant
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage1
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage2
import io.paritytech.polkadotapp.chains.storage.source.query.api.storage3
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.chains.util.coinage
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import java.math.BigInteger

typealias TokenPeriod = BigIntegerSerializable
typealias CounterAlias = DataByteArray

@JvmInline
value class CoinageApi(override val module: Module) : QueryableModule

context(WithRuntime)
val RuntimeMetadata.coinage: CoinageApi
    get() = CoinageApi(coinage())

context(WithRuntime)
val CoinageApi.minExponent: Int
    get() = constant("MinimumExponent")

context(WithRuntime)
val CoinageApi.maxExponent: Int
    get() = constant("MaximumExponent")

context(WithRuntime)
val CoinageApi.consumedFreeUnloadTokens: QueryableStorageEntry2<TokenPeriod, CounterAlias, Unit>
    get() = storage2("ConsumedFreeUnloadTokens")

context(WithRuntime)
val CoinageApi.coinsByOwner: QueryableStorageEntry1<AccountId, OnChainCoinInfo>
    get() = storage1("CoinsByOwner")

context(WithRuntime)
val CoinageApi.underlyingAssetUnit: BigInteger
    get() = constant("UnderlyingAssetUnit")

context(WithRuntime)
val CoinageApi.unloadTokenTimePeriodPeopleLitePeople: Long
    get() = constant("UnloadTokenTimePeriodPeopleLitePeople")

context(WithRuntime)
val CoinageApi.recyclersCoinToRecycler: QueryableStorageEntry1<BandersnatchPublicKey, BigInteger>
    get() = storage1("RecyclersCoinToRecycler")

context(WithRuntime)
val CoinageApi.recyclersUnloaded: QueryableStorageEntry3<BigInteger, BigInteger, ByteArray, Unit>
    get() = storage3("RecyclersUnloaded")

context(WithRuntime)
val CoinageApi.unloadTokenPerTimePeriodForLitePeople: Long
    get() = constant("UnloadTokenPerTimePeriodForLitePeople")

context(WithRuntime)
val CoinageApi.unloadTokenPerTimePeriodForPeople: Long
    get() = constant("UnloadTokenPerTimePeriodForPeople")

context(WithRuntime)
val CoinageApi.maxConsolidation: Int
    get() = constant("MaxConsolidation")
