package io.paritytech.polkadotapp.chains.storage.source.query.api

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.Scale
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.decode
import io.novasama.substrate_sdk_android.runtime.RuntimeSnapshot
import io.novasama.substrate_sdk_android.runtime.metadata.module.StorageEntry
import io.novasama.substrate_sdk_android.runtime.metadata.storageKey
import io.paritytech.polkadotapp.chains.storage.source.query.StorageQueryContext
import io.paritytech.polkadotapp.chains.storage.source.query.WithRawValue
import io.paritytech.polkadotapp.chains.util.WithRuntime
import io.paritytech.polkadotapp.chains.util.decode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlin.reflect.KType

typealias QueryableStorageBinder0<V> = (dynamicInstance: Any) -> V

interface QueryableStorageEntry0<T : Any> {
    val meta: StorageEntry

    context(StorageQueryContext)
    suspend fun query(): T?

    context(StorageQueryContext)
    suspend fun queryRaw(): String?

    context(StorageQueryContext)
    fun observe(): Flow<T?>

    context(StorageQueryContext)
    fun observeWithRaw(): Flow<WithRawValue<T?>>

    fun storageKey(): String

    fun decode(scale: String?): T?
}

context(StorageQueryContext)
fun <T : Any> QueryableStorageEntry0<T>.observeNonNull(): Flow<T> = observe().filterNotNull()

context(StorageQueryContext)
suspend fun <T : Any> QueryableStorageEntry0<T>.queryNonNull(): T = requireNotNull(query())

context(WithRuntime)
fun <T : Any> QueryableStorageEntry0<T>.decodeNonNull(scale: String?): T = requireNotNull(decode(scale))

internal class RealQueryableStorageEntry0<T : Any>(
    private val storageEntry: StorageEntry,
    private val encoders: Entry0Encoders<T>,
    private val runtimeSnapshot: RuntimeSnapshot,
) : QueryableStorageEntry0<T> {
    override val meta: StorageEntry = storageEntry

    context(StorageQueryContext)
    override suspend fun query(): T? {
        return storageEntry.query(binding = { decoded -> decoded?.let(encoders::decodeValue) })
    }

    context(StorageQueryContext)
    override fun observe(): Flow<T?> {
        return storageEntry.observe(binding = { decoded -> decoded?.let(encoders::decodeValue) })
    }

    context(StorageQueryContext)
    override fun observeWithRaw(): Flow<WithRawValue<T?>> {
        return storageEntry.observeWithRaw(binding = { decoded -> decoded?.let(encoders::decodeValue) })
    }

    context(StorageQueryContext)
    override suspend fun queryRaw(): String? {
        return storageEntry.queryRaw()
    }

    override fun storageKey(): String {
        return storageEntry.storageKey()
    }

    override fun decode(scale: String?): T? {
        return storageEntry.decode(runtimeSnapshot, scale)?.let(encoders::decodeValue)
    }
}

sealed interface Entry0Encoders<T> {
    fun decodeValue(instance: Any): T

    class Auto<T>(
        private val valueType: KType
    ) : Entry0Encoders<T> {
        override fun decodeValue(instance: Any): T {
            return Scale.decode(valueType, instance)
        }
    }

    class Manual<T>(
        private val binding: QueryableStorageBinder0<T>,
    ) : Entry0Encoders<T> {
        override fun decodeValue(instance: Any): T {
            return binding(instance)
        }
    }
}
