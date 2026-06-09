package io.paritytech.polkadotapp.common.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Cache that caches already retrieved keys and upon subsequent requests only grequests missing keys
 * [K] should be a valid type for a HashMap key
 */
interface AccumulatingMapCache<K, V> {
    /**
     * @return [Result.success] if all keys has been fetched successfully [Result.failure] otherwise
     */
    suspend fun get(keys: Iterable<K>): Result<Map<K, V>>
}

fun <K, V> AccumulatingMapCache(resolveItems: suspend (List<K>) -> Result<Map<K, V>>): AccumulatingMapCache<K, V> {
    return RealAccumulatingMapCache(resolveItems)
}

internal class RealAccumulatingMapCache<K, V>(
    private val resolveItems: suspend (List<K>) -> Result<Map<K, V>>
) : AccumulatingMapCache<K, V> {
    private val cache = mutableMapOf<K, V>()
    private val cacheAccessMutex = Mutex()

    override suspend fun get(keys: Iterable<K>): Result<Map<K, V>> = cacheAccessMutex.withLock {
        val missingKeys = keys - cache.keys
        if (missingKeys.isEmpty()) {
            return@withLock Result.success(cache)
        }

        resolveItems(missingKeys)
            .onFailure { Timber.e(it, "Failed to get items") }
            .map {
                cache.putAll(it)
                cache
            }
    }
}
